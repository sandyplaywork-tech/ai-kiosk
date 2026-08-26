import { useState } from "react";
import { login, ApiError } from "../api";

const ERROR_MESSAGES = {
  invalid_credentials: "Invalid username/password or voucher code.",
  voucher_already_used: "That login has already been used - ask staff for a new one.",
};

export default function LoginForm({ onLogin, endedMessage }) {
  const [mode, setMode] = useState("credentials");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [voucherCode, setVoucherCode] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const payload = mode === "voucher" ? { voucherCode } : { username, password };
      const session = await login(payload);
      onLogin(session);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(ERROR_MESSAGES[err.code] || "Login failed. Please check your details and try again.");
      } else {
        setError("Could not reach the server. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-shell">
      <h1>AI Kiosk</h1>
      {endedMessage && <p className="ended-banner">{endedMessage}</p>}

      <div className="login-mode-toggle">
        <button
          type="button"
          className={mode === "credentials" ? "active" : ""}
          onClick={() => setMode("credentials")}
        >
          Username &amp; password
        </button>
        <button
          type="button"
          className={mode === "voucher" ? "active" : ""}
          onClick={() => setMode("voucher")}
        >
          Voucher code
        </button>
      </div>

      <form onSubmit={handleSubmit}>
        {mode === "credentials" ? (
          <>
            <label>
              Username
              <input value={username} onChange={(e) => setUsername(e.target.value)} required />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>
          </>
        ) : (
          <label>
            Voucher code
            <input value={voucherCode} onChange={(e) => setVoucherCode(e.target.value)} required />
          </label>
        )}

        {error && <p className="form-error">{error}</p>}

        <button type="submit" disabled={submitting}>
          {submitting ? "Starting session..." : "Start session"}
        </button>
      </form>
    </div>
  );
}
