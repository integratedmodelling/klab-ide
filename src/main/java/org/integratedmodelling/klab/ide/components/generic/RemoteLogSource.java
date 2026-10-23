package org.integratedmodelling.klab.ide.components.generic;

import java.util.Objects;
import java.util.function.Function;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.klab.api.services.logging.*;

/** Uses the selected service's existing authenticated administration client; no credential store. */
public final class RemoteLogSource implements LogSource {
  private final Function<LogQuery, LogPage> query;
  private String cursor;
  private boolean follow, closed;
  private long generation;

  public RemoteLogSource(BaseServiceClient client) { this(client::queryLogs); }
  public RemoteLogSource(Function<LogQuery, LogPage> query) { this.query=Objects.requireNonNull(query); }

  @Override public LogPage poll() {
    LogQuery request;
    long selected;
    synchronized(this) {
      if (closed) throw new IllegalStateException("Log source is closed");
      request=new LogQuery("service",cursor,200,follow,null,null,null,null);
      selected=generation;
    }
    var page=query.apply(request);
    if (page == null) throw new IllegalStateException("Log service returned no page");
    synchronized(this) {
      if(!closed && selected==generation) { cursor=page.nextCursor(); follow=page.caughtUp(); }
    }
    return page;
  }
  @Override public synchronized void reset() { generation++;cursor=null;follow=false;closed=false; }
  @Override public synchronized void close() { generation++;closed=true; }
}
