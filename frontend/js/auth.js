(function () {
  const API_BASE = window.MINDCARE_API_BASE;

  function showError(form, message) {
    const el = form.querySelector("[data-error]");
    el.textContent = message;
    el.hidden = false;
  }

  function hideError(form) {
    const el = form.querySelector("[data-error]");
    el.hidden = true;
  }

  function setBusy(form, busy) {
    const button = form.querySelector("button[type=submit]");
    button.disabled = busy;
    button.style.opacity = busy ? "0.6" : "";
  }

  async function submitJson(path, payload) {
    let res;
    try {
      res = await fetch(API_BASE + path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
    } catch (_) {
      throw new Error("Could not reach the server. Please try again in a moment.");
    }

    let body = null;
    try {
      body = await res.json();
    } catch (_) {
      // no body
    }

    if (!res.ok) {
      const message = (body && body.error) || "Something went wrong. Please try again.";
      throw new Error(message);
    }

    return body;
  }

  function storeSession(authResponse) {
    localStorage.setItem("mindcare_token", authResponse.token);
    localStorage.setItem("mindcare_user", JSON.stringify(authResponse.user));
  }

  function goToDashboard() {
    window.location.href = "dashboard.html";
  }

  const loginForm = document.getElementById("login-form");
  const registerForm = document.getElementById("register-form");

  if (loginForm) {
    loginForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      hideError(loginForm);
      setBusy(loginForm, true);

      const data = new FormData(loginForm);
      try {
        const auth = await submitJson("/api/auth/login", {
          email: data.get("email"),
          password: data.get("password"),
        });
        storeSession(auth);
        goToDashboard();
      } catch (err) {
        showError(loginForm, err.message);
      } finally {
        setBusy(loginForm, false);
      }
    });
  }

  if (registerForm) {
    registerForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      hideError(registerForm);
      setBusy(registerForm, true);

      const data = new FormData(registerForm);
      try {
        const auth = await submitJson("/api/auth/register", {
          fullName: data.get("fullName"),
          email: data.get("email"),
          password: data.get("password"),
        });
        storeSession(auth);
        goToDashboard();
      } catch (err) {
        showError(registerForm, err.message);
      } finally {
        setBusy(registerForm, false);
      }
    });
  }
})();
