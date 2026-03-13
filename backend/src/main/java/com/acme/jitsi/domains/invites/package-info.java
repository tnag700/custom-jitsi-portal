@org.springframework.modulith.ApplicationModule(
    id = "invites",
    displayName = "Invites",
    allowedDependencies = {"meetings::service", "meetings::usecase", "store"},
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.acme.jitsi.domains.invites;