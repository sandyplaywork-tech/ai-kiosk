import { useState } from "react";
import LoginForm from "./components/LoginForm";
import ChatScreen from "./components/ChatScreen";
import AdminDashboard from "./components/AdminDashboard";
import "./App.css";

export default function App() {
  const [session, setSession] = useState(null);
  const [endedMessage, setEndedMessage] = useState(null);

  function handleLogin(sessionData) {
    setEndedMessage(null);
    setSession(sessionData);
  }

  function handleSessionEnded(message) {
    setSession(null);
    setEndedMessage(message);
  }

  // Staff dashboard lives at /admin on this same app/origin - a separate
  // path, not a separate deployment. It's gated by the admin key itself
  // (AdminApiKeyFilter), not by network isolation; see the CORS comment in
  // WebConfig for the tradeoff.
  if (window.location.pathname.startsWith("/admin")) {
    return <AdminDashboard />;
  }

  if (session) {
    return <ChatScreen session={session} onSessionEnded={handleSessionEnded} />;
  }

  return <LoginForm onLogin={handleLogin} endedMessage={endedMessage} />;
}
