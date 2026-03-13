@org.springframework.modulith.ApplicationModule(
    id = "health",
    displayName = "Health",
    allowedDependencies = {"configsets::service", "meetings::service"},
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.acme.jitsi.domains.health;