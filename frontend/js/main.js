(function () {
  const navItems = document.querySelectorAll(".nav-item[data-target]");
  const screens = document.querySelectorAll(".screen");
  const dropletTransition = document.querySelector(".droplet-transition");

  let isAnimating = false;

  function activateScreen(target) {
    screens.forEach((screen) => {
      screen.classList.toggle("active", screen.id === "screen-" + target);
    });
    navItems.forEach((item) => {
      item.classList.toggle("active", item.dataset.target === target);
    });
  }

  function popToScreen(target, sourceEl) {
    if (isAnimating || target === currentTarget()) return;
    isAnimating = true;

    const rect = sourceEl.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const viewportDiagonal = Math.hypot(window.innerWidth, window.innerHeight);
    const popScale = (viewportDiagonal * 1.15) / 40;

    dropletTransition.style.left = cx + "px";
    dropletTransition.style.top = cy + "px";
    dropletTransition.style.setProperty("--pop-scale", popScale.toFixed(2));
    dropletTransition.classList.add("popping");

    window.setTimeout(() => {
      activateScreen(target);
    }, 450);

    dropletTransition.addEventListener(
      "animationend",
      function onDone() {
        dropletTransition.classList.remove("popping");
        dropletTransition.removeEventListener("animationend", onDone);
        isAnimating = false;
      },
      { once: true }
    );
  }

  function currentTarget() {
    const active = document.querySelector(".screen.active");
    return active ? active.id.replace("screen-", "") : null;
  }

  navItems.forEach((item) => {
    item.addEventListener("click", () => {
      popToScreen(item.dataset.target, item);
    });
  });
})();
