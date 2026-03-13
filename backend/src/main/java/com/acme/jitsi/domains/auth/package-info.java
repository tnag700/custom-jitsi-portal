@org.springframework.modulith.ApplicationModule(
    id = "auth",
    displayName = "Authentication",
    allowedDependencies = {"meetings::service", "store"},
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.acme.jitsi.domains.auth;