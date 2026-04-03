@org.springframework.modulith.ApplicationModule(
    id = "admin",
    displayName = "Admin",
    allowedDependencies = {"health::service", "health::dto", "configsets::service"},
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED)
package com.acme.jitsi.domains.admin;