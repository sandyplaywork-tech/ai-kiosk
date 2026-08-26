import { useCallback, useEffect, useState } from "react";
import { fetchAdminSessions, killSession, ApiError } from "../api";
import IssueCredentialPanel from "./IssueCredentialPanel";

const POLL_MS = 5000;

function formatTime(totalSeconds) {
  const clamped = Math.max(0, totalSeconds);
  const minutes = Math.floor(clamped / 60);
  const seconds = clamped % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export default function AdminDashboard() {
  const [adminKey, setAdminKey] = useState(null);
  const [keyInput, setKeyInput] = useState("");
  const [unlockError, setUnlockError] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [listError, setListError] = useState(null);
  const [killTarget, setKillTarget] = useState(null);
  const [killing, setKilling] = useState(false);

  const loadSessions = useCallback(async (key) => {
    try {
      const data = await fetchAdminSessions(key);
      setSessions(data);
      setListError(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setAdminKey(null);
        setUnlockError("That admin key was rejected.");
      } else {
        setListError("Couldn't refresh the session list. Retrying...");
      }
    }
  }, []);

  useEffect(() => {
    if (!adminKey) {
      return;
    }
    let cancelled = false;
    async function poll() {
      if (!cancelled) {
        await loadSessions(adminKey);
      }
    }
    poll();
    const interval = setInterval(poll, POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [adminKey, loadSessions]);

  function handleUnlock(e) {
    e.preventDefault();
    setUnlockError(null);
    setAdminKey(keyInput.trim());
  }

  async function confirmKill() {
    setKilling(true);
    try {
      await killSession(adminKey, killTarget);
      setSessions((prev) => prev.filter((s) => s.sessionId !== killTarget));
      setKillTarget(null);
    } catch {
      setListError("Couldn't end that session. Please try again.");
    } finally {
      setKilling(false);
    }
  }

  if (!adminKey) {
    return (
      <div className="login-shell">
        <h1>Staff Admin</h1>
        <form onSubmit={handleUnlock}>
          <label>
            Admin key
            <input
              type="password"
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              required
              autoFocus
            />
          </label>
          {unlockError && <p className="form-error">{unlockError}</p>}
          <button type="submit">Unlock</button>
        </form>
      </div>
    );
  }

  return (
    <div className="admin-shell">
      <IssueCredentialPanel adminKey={adminKey} onError={setListError} />

      <h1>Active Sessions ({sessions.length})</h1>
      {listError && <div className="chat-error">{listError}</div>}

      <table className="admin-table">
        <thead>
          <tr>
            <th>User</th>
            <th>Status</th>
            <th>Time remaining</th>
            <th>Tokens remaining</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((s) => (
            <tr key={s.sessionId}>
              <td>{s.label}</td>
              <td>{s.paused ? "Paused" : "Active"}</td>
              <td>{formatTime(s.timeRemainingSeconds)}</td>
              <td>
                {s.tokensRemaining.toLocaleString()} / {s.tokenCap.toLocaleString()}
              </td>
              <td>
                <button type="button" className="kill-button" onClick={() => setKillTarget(s.sessionId)}>
                  End
                </button>
              </td>
            </tr>
          ))}
          {sessions.length === 0 && (
            <tr>
              <td colSpan="5" className="empty-hint">
                No active sessions.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {killTarget && (
        <div className="modal-overlay">
          <div className="modal-dialog">
            <p>End this session immediately? This is a forced kill - no grace period, and it can't be undone.</p>
            <div className="modal-actions">
              <button
                type="button"
                className="modal-cancel"
                onClick={() => setKillTarget(null)}
                disabled={killing}
              >
                Cancel
              </button>
              <button type="button" className="modal-confirm" onClick={confirmKill} disabled={killing}>
                {killing ? "Ending..." : "End session"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
