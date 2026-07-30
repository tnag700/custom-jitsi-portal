// Loaded after the image-provided config.js by the Jitsi Meet web container.
config.prejoinConfig = {
  enabled: true,
};
config.disableDeepLinking = true;
config.enableWelcomePage = false;
config.enableClosePage = true;
config.portalReturnUrl =
  '<!--# echo var="portal_return_url" default="" -->';

(() => {
  const fragment = new URLSearchParams(window.location.hash.slice(1));
  if (fragment.has("jwt")) {
    return;
  }

  let portalUrl;
  try {
    portalUrl = new URL(config.portalReturnUrl);
  } catch {
    window.location.replace("/");
    return;
  }

  if (
    !["https:", "http:"].includes(portalUrl.protocol) ||
    portalUrl.username ||
    portalUrl.password ||
    portalUrl.search ||
    portalUrl.hash
  ) {
    window.location.replace("/");
    return;
  }

  window.location.replace(portalUrl.toString());
})();
