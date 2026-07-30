(() => {
  const target = new URL("/", window.location.origin);
  window.setTimeout(() => window.location.replace(target), 1500);
})();
