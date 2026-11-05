package io.masterdata.common.catalog.infrastructure.metrics;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Métricas de las peticiones a catálogos que {@code http.server.requests} no puede dar por Actuator:
 * <ul>
 *   <li>{@value #TIMER} (etiqueta {@code catalog}) y {@value #TIMER_ALL}: temporizadores con percentiles
 *       p95/p99 ({@code /actuator/metrics/catalog.requests.percentile?tag=phi:0.95&tag=catalog:province}).
 *       Los percentiles de {@code http.server.requests} van repartidos en una serie por método, estado y uri,
 *       y Actuator los suma, lo que no tiene sentido; aquí hay una sola serie por catálogo.</li>
 *   <li>{@value #COUNTER} (etiquetas {@code method} y {@code status}): peticiones por método y código de estado,
 *       sin las de monitorización ni las de otras rutas.</li>
 * </ul>
 * Solo cuentan las peticiones con {@code catalog} distinto de {@code none} (ver {@link CatalogRequestObservationConvention}).
 */
@Component
public class CatalogRequestObservationHandler implements ObservationHandler<ServerRequestObservationContext> {

    static final String TIMER = "catalog.requests";
    static final String TIMER_ALL = "catalog.requests.all";
    static final String COUNTER = "catalog.requests.by";

    private final MeterRegistry registry;

    public CatalogRequestObservationHandler(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStart(ServerRequestObservationContext context) {
        context.put(Timer.Sample.class, Timer.start(registry));
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        String catalog = tag(context, "catalog");
        Timer.Sample sample = context.get(Timer.Sample.class);
        if (sample == null || catalog == null || CatalogRequestObservationConvention.NONE.equals(catalog)) {
            return;
        }
        long nanos = sample.stop(timer(TIMER, "catalog", catalog));
        timer(TIMER_ALL).record(nanos, TimeUnit.NANOSECONDS);
        registry.counter(COUNTER, "method", orUnknown(tag(context, "method")), "status", orUnknown(tag(context, "status"))).increment();
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).publishPercentiles(0.95, 0.99).register(registry);
    }

    private static String tag(ServerRequestObservationContext context, String key) {
        KeyValue value = context.getLowCardinalityKeyValue(key);
        return value == null ? null : value.getValue();
    }

    private static String orUnknown(String value) {
        return value == null ? "UNKNOWN" : value;
    }
}
