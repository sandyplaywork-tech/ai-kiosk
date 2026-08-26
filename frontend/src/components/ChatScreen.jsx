import { useEffect, useRef, useState } from "react";
import {
  sendChatMessage,
  fetchSessionStatus,
  fetchChatHistory,
  logoutSession,
  extendSession,
  exportChatTranscript,
  ApiError,
} from "../api";
import SessionStatusBar from "./SessionStatusBar";

const EXTEND_ERROR_MESSAGES = {
  voucher_not_found: "That code isn't recognized.",
  voucher_already_used: "That code has already been used.",
};

const EXPORT_ERROR_MESSAGES = {
  invalid_email: "That doesn't look like a valid email address.",
  email_delivery_failed: "Couldn't send the email right now. Please try again.",
};

const STATUS_POLL_MS = 5000;

export default function ChatScreen({ session, onSessionEnded }) {
  const [status, setStatus] = useState({
    tokenCap: session.tokenCap,
    tokensRemaining: session.tokensRemaining ?? session.tokenCap,
    timeRemainingSeconds: session.timeRemainingSeconds,
    warningMinutesRemaining: 5,
    warningTokenPercent: 80,
  });
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [showExtendModal, setShowExtendModal] = useState(false);
  const [extendCode, setExtendCode] = useState("");
  const [extending, setExtending] = useState(false);
  const [extendError, setExtendError] = useState(null);
  const [showExportModal, setShowExportModal] = useState(false);
  const [exportEmail, setExportEmail] = useState("");
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const [exportSent, setExportSent] = useState(false);
  const messagesEndRef = useRef(null);
  const endedRef = useRef(false);

  function endSession(message) {
    if (endedRef.current) {
      return;
    }
    endedRef.current = true;
    onSessionEnded(message);
  }

  // Local tick keeps the countdown smooth between polls.
  useEffect(() => {
    const tick = setInterval(() => {
      setStatus((prev) => {
        if (prev.timeRemainingSeconds <= 1) {
          endSession("Your session time has run out.");
          return prev;
        }
        return { ...prev, timeRemainingSeconds: prev.timeRemainingSeconds - 1 };
      });
    }, 1000);
    return () => clearInterval(tick);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Server poll resyncs against Redis's actual TTL/token counter and catches
  // expiry even if the tab was backgrounded and missed local ticks.
  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const data = await fetchSessionStatus(session.sessionId);
        if (!cancelled) {
          setStatus((prev) => ({ ...prev, ...data }));
        }
      } catch (err) {
        if (!cancelled && err instanceof ApiError && err.status === 404) {
          endSession("Your session has expired.");
        }
      }
    }

    poll();
    const interval = setInterval(poll, STATUS_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.sessionId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // A resumed session already has history server-side (kept for LLM context
  // regardless); reload it here so the chat window isn't blank on return.
  useEffect(() => {
    let cancelled = false;
    fetchChatHistory(session.sessionId)
      .then((history) => {
        if (!cancelled && Array.isArray(history) && history.length > 0) {
          setMessages(history.map((turn) => ({ role: turn.role, content: turn.content })));
        }
      })
      .catch(() => {
        // Fresh session or history unavailable - starting with an empty
        // chat window is the right fallback either way.
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.sessionId]);

  async function handleSend(e) {
    e.preventDefault();
    const text = input.trim();
    if (!text || sending || status.tokensRemaining <= 0) {
      return;
    }

    setMessages((prev) => [...prev, { role: "user", content: text }]);
    setInput("");
    setSending(true);
    setError(null);

    try {
      const reply = await sendChatMessage(session.sessionId, text);
      setMessages((prev) => [...prev, { role: "assistant", content: reply.reply }]);
      setStatus((prev) => ({
        ...prev,
        tokensRemaining: reply.tokensRemaining,
        timeRemainingSeconds: reply.timeRemainingSeconds,
      }));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        endSession("Your session has expired.");
        return;
      }
      if (err instanceof ApiError && err.code === "token_limit_reached") {
        setStatus((prev) => ({ ...prev, tokensRemaining: 0 }));
        setError("You've used up your token budget for this session.");
      } else {
        setError("Something went wrong reaching the assistant. Please try again.");
      }
    } finally {
      setSending(false);
    }
  }

  async function confirmLogout() {
    setLoggingOut(true);
    try {
      await logoutSession(session.sessionId);
    } catch {
      // Best-effort - the customer wants to leave either way, and the Redis
      // TTL will clean the session up on its own if this call failed.
    } finally {
      endSession("You've ended your session.");
    }
  }

  function openExtendModal() {
    setExtendCode("");
    setExtendError(null);
    setShowExtendModal(true);
  }

  async function confirmExtend(e) {
    e.preventDefault();
    const code = extendCode.trim();
    if (!code || extending) {
      return;
    }
    setExtending(true);
    setExtendError(null);
    try {
      const result = await extendSession(session.sessionId, code);
      setStatus((prev) => ({
        ...prev,
        tokenCap: result.tokenCap,
        tokensRemaining: result.tokensRemaining,
        timeRemainingSeconds: result.timeRemainingSeconds,
      }));
      setShowExtendModal(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404 && err.code === "session_expired") {
        endSession("Your session has expired.");
        return;
      }
      setExtendError(
        (err instanceof ApiError && EXTEND_ERROR_MESSAGES[err.code]) ||
          "Couldn't add time with that code. Please try again."
      );
    } finally {
      setExtending(false);
    }
  }

  function openExportModal() {
    setExportEmail("");
    setExportError(null);
    setExportSent(false);
    setShowExportModal(true);
  }

  async function confirmExport(e) {
    e.preventDefault();
    const email = exportEmail.trim();
    if (!email || exporting) {
      return;
    }
    setExporting(true);
    setExportError(null);
    try {
      await exportChatTranscript(session.sessionId, email);
      setExportSent(true);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404 && err.code === "session_expired") {
        endSession("Your session has expired.");
        return;
      }
      setExportError(
        (err instanceof ApiError && EXPORT_ERROR_MESSAGES[err.code]) ||
          "Couldn't send the transcript. Please try again."
      );
    } finally {
      setExporting(false);
    }
  }

  const inputDisabled = sending || status.tokensRemaining <= 0;
  const logoutMinutes = Math.ceil(status.timeRemainingSeconds / 60);

  return (
    <div className="chat-shell">
      <div className="chat-header">
        <SessionStatusBar {...status} />
        <div className="chat-header-actions">
          <button type="button" className="extend-button" onClick={openExportModal}>
            Email transcript
          </button>
          <button type="button" className="extend-button" onClick={openExtendModal}>
            Add time
          </button>
          <button
            type="button"
            className="logout-button"
            onClick={() => setShowLogoutConfirm(true)}
          >
            Logout
          </button>
        </div>
      </div>

      {showLogoutConfirm && (
        <div className="modal-overlay">
          <div className="modal-dialog">
            <p>
              End session? You have {logoutMinutes} minute{logoutMinutes === 1 ? "" : "s"} /{" "}
              {status.tokensRemaining.toLocaleString()} tokens remaining.
            </p>
            <div className="modal-actions">
              <button
                type="button"
                className="modal-cancel"
                onClick={() => setShowLogoutConfirm(false)}
                disabled={loggingOut}
              >
                Cancel
              </button>
              <button
                type="button"
                className="modal-confirm"
                onClick={confirmLogout}
                disabled={loggingOut}
              >
                {loggingOut ? "Ending..." : "End session"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showExtendModal && (
        <div className="modal-overlay">
          <div className="modal-dialog">
            <form onSubmit={confirmExtend}>
              <p>Have another voucher code? Add it to extend this session's time and tokens.</p>
              <input
                className="extend-code-input"
                value={extendCode}
                onChange={(e) => setExtendCode(e.target.value)}
                placeholder="Voucher code"
                disabled={extending}
                autoFocus
              />
              {extendError && <p className="form-error">{extendError}</p>}
              <div className="modal-actions">
                <button
                  type="button"
                  className="modal-cancel"
                  onClick={() => setShowExtendModal(false)}
                  disabled={extending}
                >
                  Cancel
                </button>
                <button type="submit" className="modal-confirm-positive" disabled={extending || !extendCode.trim()}>
                  {extending ? "Adding..." : "Add time"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showExportModal && (
        <div className="modal-overlay">
          <div className="modal-dialog">
            {exportSent ? (
              <>
                <p>Sent! Check your inbox for the transcript.</p>
                <div className="modal-actions">
                  <button type="button" className="modal-confirm-positive" onClick={() => setShowExportModal(false)}>
                    Done
                  </button>
                </div>
              </>
            ) : (
              <form onSubmit={confirmExport}>
                <p>Email yourself a copy of this conversation before it's gone.</p>
                <input
                  className="extend-code-input"
                  type="text"
                  value={exportEmail}
                  onChange={(e) => setExportEmail(e.target.value)}
                  placeholder="you@example.com"
                  disabled={exporting}
                  autoFocus
                />
                <p className="consent-note">
                  We'll use this address once to send your transcript, then discard it - we don't store your email.
                </p>
                {exportError && <p className="form-error">{exportError}</p>}
                <div className="modal-actions">
                  <button
                    type="button"
                    className="modal-cancel"
                    onClick={() => setShowExportModal(false)}
                    disabled={exporting}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="modal-confirm-positive"
                    disabled={exporting || !exportEmail.trim()}
                  >
                    {exporting ? "Sending..." : "Send"}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      <div className="message-list">
        {messages.length === 0 && <p className="empty-hint">Say hello to get started.</p>}
        {messages.map((message, index) => (
          <div key={index} className={`message message-${message.role}`}>
            {message.content}
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {error && <div className="chat-error">{error}</div>}

      <form className="chat-input-row" onSubmit={handleSend}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={inputDisabled ? "Session limit reached" : "Type a message..."}
          disabled={inputDisabled}
        />
        <button type="submit" disabled={inputDisabled || !input.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}
