package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Filter that injects a data-theme attribute into HTML responses when a server-set
 * cookie `app-theme` is present. The filter is careful to only modify
 * text/html responses, skips compressed responses, and avoids double-injecting.
 */
public class IndexThemeFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(IndexThemeFilter.class);

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // Fast-path: only consider requests that accept HTML to avoid buffering static assets
        String accept = request.getHeader("Accept");
        if (accept != null && !accept.contains("text/html")) {
            chain.doFilter(request, response);
            return;
        }

        BufferingResponseWrapper wrapped = new BufferingResponseWrapper(response);
        chain.doFilter(request, wrapped);

        // If response is compressed, do not attempt to modify it
        String contentEncoding = wrapped.getHeader("Content-Encoding");
        if (contentEncoding != null && !contentEncoding.isEmpty()) {
            wrapped.commitToResponse();
            return;
        }

        String contentType = wrapped.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("text/html")) {
            wrapped.commitToResponse();
            return;
        }

        Charset charset = StandardCharsets.UTF_8;
        try {
            String enc = wrapped.getCharacterEncoding();
            if (enc != null) charset = Charset.forName(enc);
        } catch (Exception e) {
            // fallback to UTF-8
        }

        String body = new String(wrapped.getData(), charset);

        // Avoid double-injection if data-theme already present on the html tag
        if (body.toLowerCase().contains("<html") && body.toLowerCase().contains("data-theme=")) {
            wrapped.commitToResponse();
            return;
        }

        // check for cookie
        String theme = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("app-theme".equals(c.getName())) {
                    theme = c.getValue();
                    break;
                }
            }
        }

        if (theme != null && "andromeda".equalsIgnoreCase(theme)) {
            // Insert attribute before the first space or '>' after <html using lookahead
            body = body.replaceFirst("(?i)<html(?=\\s|>)", "<html data-theme=\"andromeda\"");
            log.debug("Injected data-theme into HTML response for request {}", request.getRequestURI());
        }

        byte[] out = body.getBytes(charset);
        HttpServletResponse resp = (HttpServletResponse) wrapped.getResponse();
        resp.setContentLength(out.length);
        resp.getOutputStream().write(out);
    }

    /**
     * Response wrapper that buffers the output so it can be modified.
     */
    private static class BufferingResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final ServletOutputStream outputStream = new ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(WriteListener writeListener) { }
            @Override
            public void write(int b) throws IOException { buffer.write(b); }
        };
        private PrintWriter writer;

        BufferingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(buffer, getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding())), true);
            }
            return writer;
        }

        byte[] getData() {
            try {
                if (writer != null) writer.flush();
            } catch (Exception ignored) {}
            return buffer.toByteArray();
        }

        void commitToResponse() throws IOException {
            byte[] data = getData();
            HttpServletResponse resp = (HttpServletResponse) getResponse();
            resp.setContentLength(data.length);
            resp.getOutputStream().write(data);
        }
    }
}
