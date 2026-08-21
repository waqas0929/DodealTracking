package com.dodeal.deviceagent.config

/** Wire-logging verbosity. Kept abstract so the HTTP layer maps it to whatever client is in use. */
enum class LoggingLevel { NONE, BASIC, HEADERS, BODY }
