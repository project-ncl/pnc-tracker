/*
 * Copyright 2022-2026 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.tracker.exception;

import java.io.NotSerializableException;
import java.io.Serializable;
import java.text.MessageFormat;

/**
 * Signals an error between the REST-resources layer and the next layer down (except for {@link DownloadManager}, which
 * is normally two layers down thanks to binding controllers). Workflow exceptions are intended to carry with them some
 * notion of what response to send to the user (even if it's the default: HTTP 500).
 */
public class TrackerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private Object[] params;

    private transient String formattedMessage;

    private int status;

    public TrackerException(final String message, final Object... params) {
        super(message);
        this.params = params;
    }

    public TrackerException(final String message, final Throwable cause, final Object... params) {
        super(message, cause);
        this.params = params;
    }

    public TrackerException(final int status, final String message, final Object... params) {
        super(message);
        this.params = params;
        this.status = status;
    }

    public TrackerException(final int status, final String message, Throwable cause, final Object... params) {
        super(message, cause);
        this.params = params;
        this.status = status;
    }

    @Override
    public synchronized String getMessage() {
        if (formattedMessage == null) {
            final String format = super.getMessage();
            if (params == null || params.length < 1) {
                formattedMessage = format;
            } else {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] == null) {
                        params[i] = "null";
                    }
                }

                final String original = formattedMessage;
                try {
                    formattedMessage = String.format(format.replaceAll("\\{}", "%s"), params);
                } catch (final Error | Exception e) {
                    // do nothing
                }

                if (formattedMessage == null || formattedMessage.equals(original)) {
                    try {
                        formattedMessage = MessageFormat.format(format, params);
                    } catch (Error | Exception e) {
                        formattedMessage = format;
                    }
                }
            }
        }

        return formattedMessage;
    }

    public int getStatus() {
        return status < 1 ? 500 : status;
    }

    /**
     * Stringify all parameters pre-emptively on serialization, to prevent {@link NotSerializableException}. Since all
     * parameters are used in {@link String#format} or {@link MessageFormat#format}, flattening them to strings is an
     * acceptable way to provide this functionality without making the use of {@link Serializable} viral.
     */
    private Object writeReplace() {
        final Object[] newParams = new Object[params.length];
        int i = 0;
        for (final Object object : params) {
            newParams[i] = String.valueOf(object);
            i++;
        }

        this.params = newParams;
        return this;
    }

}
