/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.logstash;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

import java.util.List;
import java.util.Map;

public final class StringInterpolation {

    private static final ThreadLocal<StringBuilder> STRING_BUILDER =
        new ThreadLocal<StringBuilder>() {
            @Override
            protected StringBuilder initialValue() {
                return new StringBuilder();
            }

            @Override
            public StringBuilder get() {
                StringBuilder b = super.get();
                b.setLength(0); // clear/reset the buffer
                return b;
            }

        };
    
    private StringInterpolation() {
        // Utility Class
    }

    public static String evaluate(final co.elastic.logstash.api.Event event, final String template)
        throws JsonProcessingException {
        if (event instanceof Event) {
            return evaluate((Event) event, template);
        } else {
            throw new IllegalStateException("Unknown event concrete class: " + event.getClass().getName());
        }
    }

    public static String evaluate(final Event event, final String template) throws JsonProcessingException {
        int open = template.indexOf("%{");
        int close = template.indexOf('}', open);
        if (open == -1 || close == -1) {
            return template;
        }
        final StringBuilder builder = STRING_BUILDER.get();
        int pos = 0;
        while (open > -1 && close > -1) {
            if (open > 0) {
                builder.append(template, pos, open);
            }
            if (template.regionMatches(open + 2, "+%s", 0, close - open - 2)) {
                Timestamp t = event.getTimestamp();
                builder.append(t == null ? "" : t.getTime().getMillis() / 1000L);
            } else if (template.charAt(open + 2) == '+') {
                Timestamp t = event.getTimestamp();
                builder.append(t != null
                        ? event.getTimestamp().getTime().toString(
                                DateTimeFormat.forPattern(template.substring(open + 3, close))
                                        .withZone(DateTimeZone.UTC))
                        : ""
                    );
            } else {
                final String found = template.substring(open + 2, close);
                final Object value = event.getField(found);
                if (value != null) {
                    if (value instanceof List) {
                        builder.append(KeyNode.join((List) value, ","));
                    } else if (value instanceof Map) {
                        builder.append(ObjectMappers.JSON_MAPPER.writeValueAsString(value));
                    } else {
                        builder.append(value.toString());
                    }
                } else {
                    builder.append("%{").append(found).append('}');
                }
            }
            pos = close + 1;
            open = template.indexOf("%{", pos);
            close = template.indexOf('}', open);
        }
        final int len = template.length();
        if (pos < len) {
            builder.append(template, pos, len);
        }
        return builder.toString();
    }

}
