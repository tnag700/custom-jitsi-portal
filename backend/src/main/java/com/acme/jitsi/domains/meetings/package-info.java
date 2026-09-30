@org.springframework.modulith.ApplicationModule(
    id = "meetings",
    displayName = "Meetings",
    allowedDependencies = {"configsets::service", "profiles::service", "rooms::service"},
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.acme.jitsi.domains.meetings;
