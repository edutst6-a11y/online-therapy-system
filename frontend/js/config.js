window.MINDCARE_API_BASE =
  location.hostname === "localhost" || location.hostname === "127.0.0.1"
    ? "http://localhost:8080"
    : "https://online-therapy-system.onrender.com";
