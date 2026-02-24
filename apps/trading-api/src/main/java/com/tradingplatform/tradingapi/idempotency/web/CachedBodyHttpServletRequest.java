package com.tradingplatform.tradingapi.idempotency.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StreamUtils;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
  private final byte[] cachedBody;

  public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
    super(request);
    this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
  }

  public byte[] getCachedBody() {
    return cachedBody;
  }

  @Override
  public ServletInputStream getInputStream() {
    return new CachedBodyServletInputStream(cachedBody);
  }

  @Override
  public BufferedReader getReader() {
    Charset charset = resolveCharset();
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }

  private Charset resolveCharset() {
    String encoding = getCharacterEncoding();
    if (encoding == null || encoding.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(encoding);
    } catch (Exception ex) {
      return StandardCharsets.UTF_8;
    }
  }

  private static class CachedBodyServletInputStream extends ServletInputStream {
    private final ByteArrayInputStream delegate;

    private CachedBodyServletInputStream(byte[] cachedBody) {
      this.delegate = new ByteArrayInputStream(cachedBody == null ? new byte[0] : cachedBody);
    }

    @Override
    public int read() {
      return delegate.read();
    }

    @Override
    public boolean isFinished() {
      return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      // Async IO is not required for this skeleton request wrapper.
    }
  }
}
