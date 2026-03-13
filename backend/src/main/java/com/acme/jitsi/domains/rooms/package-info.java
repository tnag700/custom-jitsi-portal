@org.springframework.modulith.ApplicationModule(
    id = "rooms",
    displayName = "Rooms",
    allowedDependencies = "configsets::service",
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.acme.jitsi.domains.rooms;