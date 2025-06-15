package com.qiujie.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

public class StartupMarkerFilter extends TurboFilter {

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (marker != null && marker.contains("STARTUP")) {
            // Only block STARTUP logs when the log level is OFF
            if (level == Level.OFF) {
                return FilterReply.DENY;
            } else {
                return FilterReply.ACCEPT;
            }
        }
        return FilterReply.NEUTRAL;
    }
}
