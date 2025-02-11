/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.horizon.minion.flows.parser;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.opennms.horizon.grpc.telemetry.contract.TelemetryMessage;
import org.opennms.horizon.minion.flows.listeners.Parser;
import org.opennms.horizon.minion.flows.parser.factory.DnsResolver;
import org.opennms.horizon.minion.flows.parser.flowmessage.FlowMessage;
import org.opennms.horizon.minion.flows.parser.ie.RecordProvider;
import org.opennms.horizon.minion.flows.parser.session.SequenceNumberTracker;
import org.opennms.horizon.minion.flows.parser.session.Session;
import org.opennms.horizon.minion.flows.parser.transport.MessageBuilder;
import org.opennms.horizon.shared.ipc.rpc.IpcIdentity;
import org.opennms.horizon.shared.ipc.sink.api.AsyncDispatcher;
import org.opennms.horizon.shared.logging.LogPreservingThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.swrve.ratelimitedlogger.RateLimitedLog;

public abstract class ParserBase implements Parser {
    private static final Logger LOG = LoggerFactory.getLogger(ParserBase.class);

    private final RateLimitedLog SEQUENCE_ERRORS_LOGGER = RateLimitedLog
            .withRateLimit(LOG)
            .maxRate(5).every(Duration.ofSeconds(30))
            .build();

    private static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    private static final long DEFAULT_CLOCK_SKEW_EVENT_RATE_SECONDS = TimeUnit.HOURS.toSeconds(1);

    private static final long DEFAULT_ILLEGAL_FLOW_EVENT_RATE_SECONDS = TimeUnit.HOURS.toSeconds(1);

    private final ThreadLocal<Boolean> isParserThread = new ThreadLocal<>();

    private final Protocol protocol;

    private final String name;

    private final AsyncDispatcher<TelemetryMessage> dispatcher;

    private final IpcIdentity identity;

    private final DnsResolver dnsResolver;

    private final Meter recordsReceived;

    private final Meter recordsScheduled;

    private final Meter recordsDispatched;

    private final Meter recordsCompleted;

    private final Counter recordEnrichmentErrors;

    private final Counter recordDispatchErrors;

    private final Meter invalidFlows;

    private final Timer recordEnrichmentTimer;

    private final Counter sequenceErrors;

    private final ThreadFactory threadFactory;

    private int threads = DEFAULT_NUM_THREADS;

    private long maxClockSkew = 0;

    private long clockSkewEventRate = 0;

    private long illegalFlowEventRate = 0;

    private static final boolean DNS_LOOKUPS_ENABLED = true;

    private LoadingCache<InetAddress, Optional<Instant>> clockSkewEventCache;

    private LoadingCache<InetAddress, Optional<Instant>> illegalFlowEventCache;

    private ExecutorService executor;

    public ParserBase(final Protocol protocol,
                      final String name,
                      final AsyncDispatcher<TelemetryMessage> dispatcher,
                      final IpcIdentity identity,
                      final DnsResolver dnsResolver,
                      final MetricRegistry metricRegistry) {
        this.protocol = Objects.requireNonNull(protocol);
        this.name = Objects.requireNonNull(name);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.identity = Objects.requireNonNull(identity);
        this.dnsResolver = Objects.requireNonNull(dnsResolver);
        Objects.requireNonNull(metricRegistry);

        // Create a thread factory that sets a thread local variable when the thread is created
        // This variable is used to identify the thread as one that belongs to this class
        final LogPreservingThreadFactory logPreservingThreadFactory = new LogPreservingThreadFactory("Telemetryd-" + protocol + "-" + name, Integer.MAX_VALUE);
        threadFactory = r -> logPreservingThreadFactory.newThread(() -> {
            if (Objects.nonNull(isParserThread.get()) && isParserThread.get()) {
                unload();
            }
            isParserThread.set(true);
            r.run();
        });

        recordsReceived = metricRegistry.meter(MetricRegistry.name("parsers",  name, "recordsReceived"));
        recordsDispatched = metricRegistry.meter(MetricRegistry.name("parsers",  name, "recordsDispatched"));
        recordEnrichmentTimer = metricRegistry.timer(MetricRegistry.name("parsers",  name, "recordEnrichment"));
        recordEnrichmentErrors = metricRegistry.counter(MetricRegistry.name("parsers",  name, "recordEnrichmentErrors"));
        invalidFlows = metricRegistry.meter(MetricRegistry.name("parsers",  name, "invalidFlows"));
        recordsScheduled = metricRegistry.meter(MetricRegistry.name("parsers",  name, "recordsScheduled"));
        recordsCompleted = metricRegistry.meter(MetricRegistry.name("parsers",  name, "recordsCompleted"));
        recordDispatchErrors = metricRegistry.counter(MetricRegistry.name("parsers",  name, "recordDispatchErrors"));
        sequenceErrors = metricRegistry.counter(MetricRegistry.name("parsers", name, "sequenceErrors"));

        // Call setters since these also perform additional handling
        setClockSkewEventRate(DEFAULT_CLOCK_SKEW_EVENT_RATE_SECONDS);
        setIllegalFlowEventRate(DEFAULT_ILLEGAL_FLOW_EVENT_RATE_SECONDS);
        setThreads(DEFAULT_NUM_THREADS);
    }

    protected abstract MessageBuilder getMessageBuilder();

    @Override
    public void start(ScheduledExecutorService executorService) {
        executor = new ThreadPoolExecutor(
                // corePoolSize must be > 0 since we use the RejectedExecutionHandler to block when the queue is full
                1, threads,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threadFactory,
                (r, executor) -> {
                    // We enter this block when the queue is full and the caller is attempting to submit additional tasks
                    try {
                        // If we're not shutdown, then block until there's room in the queue
                        if (!executor.isShutdown()) {
                            executor.getQueue().put(r);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RejectedExecutionException("Executor interrupted while waiting for capacity in the work queue.", e);
                    }
                });
    }

    @Override
    public void stop() {
        executor.shutdown();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.protocol.description;
    }

    public void setMaxClockSkew(final long maxClockSkew) {
        this.maxClockSkew = maxClockSkew;
    }

    public long getMaxClockSkew() {
        return this.maxClockSkew;
    }

    public long getClockSkewEventRate() {
        return clockSkewEventRate;
    }

    public void setClockSkewEventRate(final long clockSkewEventRate) {
        this.clockSkewEventRate = clockSkewEventRate;

        this.clockSkewEventCache = CacheBuilder.newBuilder().expireAfterWrite(this.clockSkewEventRate, TimeUnit.SECONDS).build(new CacheLoader<>() {
            @Override
            public Optional<Instant> load(InetAddress key) {
                return Optional.empty();
            }
        });
    }

    public void setIllegalFlowEventRate(final long illegalFlowEventRate) {
        this.illegalFlowEventRate = illegalFlowEventRate;

        this.illegalFlowEventCache = CacheBuilder.newBuilder().expireAfterWrite(this.illegalFlowEventRate, TimeUnit.SECONDS).build(new CacheLoader<>() {
            @Override
            public Optional<Instant> load(InetAddress key) {
                return Optional.empty();
            }
        });
    }

    public long getIllegalFlowEventRate() {
        return illegalFlowEventRate;
    }

    public boolean getDnsLookupsEnabled() {
        return DNS_LOOKUPS_ENABLED;
    }

    public void setThreads(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("Threads must be >= 1");
        }
        this.threads = threads;
    }

    protected CompletableFuture<?> transmit(final RecordProvider packet, final Session session, final InetSocketAddress remoteAddress) {
        // Verify that flows sequences are in order
        if (!session.verifySequenceNumber(packet.getObservationDomainId(), packet.getSequenceNumber())) {
            SEQUENCE_ERRORS_LOGGER.warn("Error in flow sequence detected: from {}", session.getRemoteAddress());
            this.sequenceErrors.inc();
        }

        // The packets are coming in hot - performance here is critical
        //   LOG.trace("Got packet: {}", packet);
        // Perform the record enrichment and serialization in a thread pool allowing these to be parallelized
        final CompletableFuture<CompletableFuture[]> futureOfFutures = CompletableFuture.supplyAsync(() -> {
            return packet.getRecords().map(record -> {
                this.recordsReceived.mark();

                final CompletableFuture<Void> future = new CompletableFuture<>();
                final Timer.Context timerContext = recordEnrichmentTimer.time();
                // Trigger record enrichment (performing DNS reverse lookups for example)
                final RecordEnricher recordEnricher = new RecordEnricher(dnsResolver, getDnsLookupsEnabled());
                recordEnricher.enrich(record).whenComplete((enrichment, ex) -> {
                    timerContext.close();
                    if (ex != null) {
                        this.recordEnrichmentErrors.inc();

                        // Enrichment failed
                        future.completeExceptionally(ex);
                        return;
                    }
                    // Enrichment was successful

                    // We're currently in the callback thread from the enrichment process
                    // We want the remainder of the serialization and dispatching to be performed
                    // from one of our executor threads so that we can put back-pressure on the listener
                    // if we can't keep up
                    final Runnable dispatch = () -> {
                        // Let's serialize
                        final FlowMessage.Builder flowMessage;
                        try {
                            flowMessage = this.getMessageBuilder().buildMessage(record, enrichment);
                        } catch (final  Exception e) {
                            throw new RuntimeException(e);
                        }

                        // Check if the flow is valid (and maybe correct it)
                        final List<String> corrections = this.correctFlow(flowMessage);
                        if (!corrections.isEmpty()) {
                            this.invalidFlows.mark();

                            final Optional<Instant> instant = illegalFlowEventCache.getUnchecked(session.getRemoteAddress());

                            if (instant.isEmpty() || Duration.between(instant.get(), Instant.now()).getSeconds() > getIllegalFlowEventRate()) {
                                illegalFlowEventCache.put(session.getRemoteAddress(), Optional.of(Instant.now()));

                                for (final String correction : corrections) {
                                    LOG.warn("Illegal flow detected from exporter {}: \n{}", session.getRemoteAddress().getAddress(), correction);
                                }
                            }
                        }

                        // Build the message to dispatch
                        final TelemetryMessage telemetryMessage = TelemetryMessage.newBuilder()
                            .setBytes(ByteString.copyFrom(flowMessage.build().toByteArray()))
                            .build();

                        // Dispatch
                        dispatcher.send(telemetryMessage).whenComplete((b, exx) -> {
                            if (exx != null) {
                                this.recordDispatchErrors.inc();
                                future.completeExceptionally(exx);
                            } else {
                                this.recordsCompleted.mark();
                                future.complete(null);
                            }
                        });

                        recordsDispatched.mark();
                    };

                    // It's possible that the callback thread is already a thread from the pool, if that's the case
                    // execute within the current thread. This helps avoid deadlocks.
                    if (Boolean.TRUE.equals(isParserThread.get())) {
                        dispatch.run();
                    } else {
                        // We're not in one of the parsers threads, execute the dispatch in the pool
                        executor.execute(dispatch);
                    }

                    this.recordsScheduled.mark();
                });
                return future;
            }).toArray(CompletableFuture[]::new);
        }, executor);

        // Return a future which is completed when all records are finished dispatching (i.e. written to Kafka)
        final CompletableFuture<Void> future = new CompletableFuture<>();
        futureOfFutures.whenComplete((futures,ex) -> {
            if (ex != null) {
                LOG.warn("Error preparing records for dispatch.", ex);
                future.completeExceptionally(ex);
                return;
            }
            // Dispatch was triggered for all the records, now wait for the dispatching to complete
            CompletableFuture.allOf(futures).whenComplete((any,exx) -> {
                if (exx != null) {
                    LOG.warn("One or more of the records were not successfully dispatched.", exx);
                    future.completeExceptionally(exx);
                    return;
                }
                // All of the records have been successfully dispatched
                future.complete(any);
            });
        });
        return future;
    }

    protected void detectClockSkew(final long packetTimestampMs, final InetAddress remoteAddress) {
        if (getMaxClockSkew() > 0) {
            long deltaMs = Math.abs(packetTimestampMs - System.currentTimeMillis());
            if (deltaMs > getMaxClockSkew() * 1000L) {
                final Optional<Instant> instant = clockSkewEventCache.getUnchecked(remoteAddress);

                if (instant.isEmpty() || Duration.between(instant.get(), Instant.now()).getSeconds() > getClockSkewEventRate()) {
                    clockSkewEventCache.put(remoteAddress, Optional.of(Instant.now()));

                   /* eventForwarder.sendNow(new EventBuilder()
                            .setUei(CLOCK_SKEW_EVENT_UEI)
                            .setTime(new Date())
                            .setSource(getName())
                            .setInterface(remoteAddress)
                            .setDistPoller(identity.getId())
                            .addParam("monitoringSystemId", identity.getId())
                            .addParam("monitoringSystemLocation", identity.getLocation())
                            .setParam("delta", (int) deltaMs)
                            .setParam("clockSkewEventRate", (int) getClockSkewEventRate())
                            .setParam("maxClockSkew", (int) getMaxClockSkew())
                            .getEvent()); */
                }

            }
        }
    }

    private List<String> correctFlow(final FlowMessage.Builder flow) {
        final List<String> corrections = Lists.newArrayList();

        if (flow.getFirstSwitched().getValue() > flow.getLastSwitched().getValue()) {
            corrections.add(String.format("Malformed flow: lastSwitched must be greater than firstSwitched: srcAddress=%s, dstAddress=%s, firstSwitched=%d, lastSwitched=%d, duration=%d",
                                  flow.getSrcAddress(),
                                  flow.getDstAddress(),
                                  flow.getFirstSwitched().getValue(),
                                  flow.getLastSwitched().getValue(),
                                  flow.getLastSwitched().getValue() - flow.getFirstSwitched().getValue()));

            // Re-calculate a (somewhat) valid timout from the flow timestamps
            final long timeout = (flow.hasDeltaSwitched() && flow.getDeltaSwitched().getValue() != flow.getFirstSwitched().getValue())
                    ? (flow.getLastSwitched().getValue() - flow.getDeltaSwitched().getValue())
                    : 0L;

            flow.getLastSwitchedBuilder().setValue(flow.getTimestamp());
            flow.getFirstSwitchedBuilder().setValue(flow.getTimestamp() - timeout);
            flow.getDeltaSwitchedBuilder().setValue(flow.getTimestamp() - timeout);
        }

        return corrections;
    }

    protected SequenceNumberTracker sequenceNumberTracker() {
        int sequenceNumberPatience = 32;
        return new SequenceNumberTracker(sequenceNumberPatience);
    }

    public void unload() {
        isParserThread.remove();
    }
}
