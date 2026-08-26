function formatTime(totalSeconds) {
  const clamped = Math.max(0, totalSeconds);
  const minutes = Math.floor(clamped / 60);
  const seconds = clamped % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export default function SessionStatusBar({
  tokenCap,
  tokensRemaining,
  timeRemainingSeconds,
  warningMinutesRemaining,
  warningTokenPercent,
}) {
  const usedPercent = tokenCap > 0
    ? Math.min(100, Math.round(((tokenCap - tokensRemaining) / tokenCap) * 100))
    : 0;
  const timeWarning = timeRemainingSeconds <= warningMinutesRemaining * 60;
  const tokenWarning = usedPercent >= warningTokenPercent;

  let warningText = null;
  if (timeWarning && tokenWarning) {
    warningText = "Heads up: your time and token budget are both almost used up.";
  } else if (timeWarning) {
    warningText = `Heads up: less than ${warningMinutesRemaining} minute${warningMinutesRemaining === 1 ? "" : "s"} remaining.`;
  } else if (tokenWarning) {
    warningText = "Heads up: you're nearing your token budget for this session.";
  }

  return (
    <div className="session-status-bar">
      <div className={`countdown ${timeWarning ? "warning" : ""}`}>{formatTime(timeRemainingSeconds)} remaining</div>
      <div className="token-bar">
        <div className={`token-bar-fill ${tokenWarning ? "warning" : ""}`} style={{ width: `${usedPercent}%` }} />
      </div>
      <div className="token-bar-label">
        {tokensRemaining.toLocaleString()} / {tokenCap.toLocaleString()} tokens remaining
      </div>
      {warningText && <div className="warning-banner">{warningText}</div>}
    </div>
  );
}
