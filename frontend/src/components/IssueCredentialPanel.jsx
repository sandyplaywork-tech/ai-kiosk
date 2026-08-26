import { useState } from "react";
import { issueCredential } from "../api";

export default function IssueCredentialPanel({ adminKey, onError }) {
  const [type, setType] = useState("USERNAME_PASSWORD");
  const [issuing, setIssuing] = useState(false);
  const [issued, setIssued] = useState(null);
  const [copied, setCopied] = useState(false);

  async function handleGenerate() {
    setIssuing(true);
    setIssued(null);
    setCopied(false);
    try {
      const result = await issueCredential(adminKey, type);
      setIssued(result);
    } catch {
      onError("Couldn't generate a new login. Please try again.");
    } finally {
      setIssuing(false);
    }
  }

  function credentialText() {
    if (!issued) {
      return "";
    }
    return issued.username
      ? `Username: ${issued.username}\nPassword: ${issued.password}`
      : `Voucher code: ${issued.voucherCode}`;
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(credentialText());
      setCopied(true);
    } catch {
      // Clipboard access can fail (permissions, insecure context) - the
      // credential is still visible on screen either way, so this is
      // non-fatal; just skip the "Copied" confirmation.
    }
  }

  return (
    <div className="issue-panel">
      <h2>Issue New Login</h2>
      <div className="login-mode-toggle">
        <button
          type="button"
          className={type === "USERNAME_PASSWORD" ? "active" : ""}
          onClick={() => setType("USERNAME_PASSWORD")}
        >
          Username &amp; password
        </button>
        <button
          type="button"
          className={type === "VOUCHER" ? "active" : ""}
          onClick={() => setType("VOUCHER")}
        >
          Voucher code
        </button>
      </div>

      <button type="button" className="modal-confirm-positive" onClick={handleGenerate} disabled={issuing}>
        {issuing ? "Generating..." : "Generate"}
      </button>

      {issued && (
        <div className="issued-credential">
          {issued.username ? (
            <>
              <div>
                Username: <span className="mono">{issued.username}</span>
              </div>
              <div>
                Password: <span className="mono">{issued.password}</span>
              </div>
            </>
          ) : (
            <div>
              Voucher code: <span className="mono">{issued.voucherCode}</span>
            </div>
          )}
          <div className="issued-meta">
            {issued.tokenCap.toLocaleString()} tokens · {issued.sessionLengthMinutes} minutes
          </div>
          <button type="button" className="modal-cancel" onClick={handleCopy}>
            {copied ? "Copied!" : "Copy"}
          </button>
        </div>
      )}
    </div>
  );
}
