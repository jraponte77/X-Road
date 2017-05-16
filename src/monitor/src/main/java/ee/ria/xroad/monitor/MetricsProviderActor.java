/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.monitor;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Snapshot;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ee.ria.xroad.monitor.common.SystemMetricNames;
import ee.ria.xroad.monitor.common.SystemMetricsRequest;
import ee.ria.xroad.monitor.common.SystemMetricsResponse;
import ee.ria.xroad.monitor.common.dto.HistogramDto;
import ee.ria.xroad.monitor.common.dto.MetricDto;
import ee.ria.xroad.monitor.common.dto.MetricSetDto;
import ee.ria.xroad.monitor.common.dto.SimpleMetricDto;
import ee.ria.xroad.monitor.executablelister.ListedData;
import ee.ria.xroad.monitor.executablelister.PackageInfo;
import ee.ria.xroad.monitor.executablelister.ProcessInfo;

/**
 * Actor for providing system metrics data
 */
public class MetricsProviderActor extends UntypedActor {

    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private SimpleDateFormat certificateDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");


    @Override
    public void onReceive(Object o) throws Exception {

        System.out.println("monitorointi juttua onReceive");
        log.info("monitorointi juttua onReceive");
        log.error("monitorointi juttua erroriin");


        if (o instanceof SystemMetricsRequest) {
            log.info("received SystemMetricsRequest");
            MetricRegistry metrics = MetricRegistryHolder.getInstance().getMetrics();
            final MetricSetDto.Builder builder = new MetricSetDto.Builder("systemMetrics");

            for (Map.Entry<String, Histogram> e : metrics.getHistograms().entrySet()) {
                builder.withMetric(toHistogramDto(e.getKey(), e.getValue().getSnapshot()));
            }
            // dont handle processes and packages gauges normally, they have have special conversions to dto
            // *_STRINGS gauges are only for JMX reporting
            for (Map.Entry<String, Gauge> e : metrics.getGauges(
                    (name, metric) ->
                    !Lists.newArrayList(SystemMetricNames.PROCESSES,
                            SystemMetricNames.PROCESS_STRINGS,
                            SystemMetricNames.XROAD_PROCESSES,
                            SystemMetricNames.XROAD_PROCESS_STRINGS,
                            SystemMetricNames.PACKAGES,
                            SystemMetricNames.PACKAGE_STRINGS,
                            SystemMetricNames.CERTIFICATES).contains(name))
                    .entrySet()) {
                builder.withMetric(toSimpleMetricDto(e.getKey(), e.getValue()));
            }

            for (Map.Entry<String, Gauge> e : metrics.getGauges(
                    (name, metric) -> SystemMetricNames.PROCESSES.equals(name)
                            || SystemMetricNames.XROAD_PROCESSES.equals(name))
                    .entrySet()) {
                builder.withMetric(toProcessMetricSetDto(e.getKey(), e.getValue()));
            }

            for (Map.Entry<String, Gauge> e : metrics.getGauges(
                    (name, metric) -> SystemMetricNames.CERTIFICATES.equals(name))
                    .entrySet()) {
                builder.withMetric(toCertificateMetricSetDTO(e.getKey(), e.getValue()));
            }

            for (Map.Entry<String, Gauge> e : metrics.getGauges(
                    (name, metric) -> SystemMetricNames.PACKAGES.equals(name))
                    .entrySet()) {
                builder.withMetric(toPackageMetricSetDto(e.getKey(), e.getValue()));
            }

            MetricSetDto metricSet = builder.build();
            final SystemMetricsResponse response = new SystemMetricsResponse(metricSet);
            getSender().tell(response, getSelf());

        } else {
            unhandled(o);
        }
    }

    private Map<String, Metric> getMetricSets(Map<String, Metric> metrics) {
        return Maps.filterValues(metrics, input -> input instanceof MetricSet);
    }

    private MetricSetDto toProcessMetricSetDto(String name,
                                        Gauge<ListedData<ProcessInfo>> processSensor) {
        ListedData<ProcessInfo> p = processSensor.getValue();
        MetricSetDto.Builder mainBuilder = new MetricSetDto.Builder(name);
        for (ProcessInfo process: p.getParsedData()) {
            MetricSetDto.Builder processBuilder = new MetricSetDto.Builder(process.getProcessId());
            processBuilder.withMetric(new SimpleMetricDto<>("processId", process.getProcessId()));
            processBuilder.withMetric(new SimpleMetricDto<>("command", process.getCommand()));
            processBuilder.withMetric(new SimpleMetricDto<>("cpuLoad", process.getCpuLoad()));
            processBuilder.withMetric(new SimpleMetricDto<>("memUsed", process.getMemUsed()));
            processBuilder.withMetric(new SimpleMetricDto<>("startTime", process.getStartTime()));
            processBuilder.withMetric(new SimpleMetricDto<>("userId", process.getUserId()));
            MetricSetDto processDto = processBuilder.build();
            mainBuilder.withMetric(processDto);
        }
        return mainBuilder.build();
    }



    private MetricSetDto toCertificateMetricSetDTO(String name,
                                               Gauge<ListedData<CertificateMonitoringInfo>> certificateSensor) {
        ListedData<CertificateMonitoringInfo> c = certificateSensor.getValue();
        MetricSetDto.Builder mainBuilder = new MetricSetDto.Builder(name);
        for (CertificateMonitoringInfo cert: c.getParsedData()) {
            MetricSetDto.Builder certBuilder = new MetricSetDto.Builder("certificate-" + cert.getId());
            certBuilder.withMetric(new SimpleMetricDto<>("subjectDN", cert.getSubject()));
            certBuilder.withMetric(new SimpleMetricDto<>("issuerDN", cert.getIssuer()));
            certBuilder.withMetric(new SimpleMetricDto<>("status", cert.getStatus()));
            certBuilder.withMetric(new SimpleMetricDto<>("notBefore",
                    formatCertificateDate(cert.getNotBefore())));
            certBuilder.withMetric(new SimpleMetricDto<>("notAfter",
                    formatCertificateDate(cert.getNotAfter())));
            MetricSetDto certDto = certBuilder.build();
            mainBuilder.withMetric(certDto);
        }
        return mainBuilder.build();
    }

    private String formatCertificateDate(Date date) {
        return certificateDateFormat.format(date);
        // TODO: signer timeout problems???
    }

    private MetricSetDto toPackageMetricSetDto(String name,
                                        Gauge<ListedData<PackageInfo>> packageSensor) {
        ListedData<PackageInfo> p = packageSensor.getValue();
        MetricSetDto.Builder mainBuilder = new MetricSetDto.Builder(name);
        for (PackageInfo pac: p.getParsedData()) {
            mainBuilder.withMetric(new SimpleMetricDto<>(pac.getName(), pac.getVersion()));
        }
        return mainBuilder.build();
    }

    private <T extends Serializable> SimpleMetricDto<T> toSimpleMetricDto(String key, Gauge<T> value) {
        return new SimpleMetricDto<>(key, value.getValue());
    }

    private MetricDto toHistogramDto(String name, Snapshot snapshot) {
        return new HistogramDto(
                name,
                snapshot.get75thPercentile(),
                snapshot.get95thPercentile(),
                snapshot.get98thPercentile(),
                snapshot.get99thPercentile(),
                snapshot.get999thPercentile(),
                snapshot.getMax(),
                snapshot.getMean(),
                snapshot.getMedian(),
                snapshot.getMin(),
                snapshot.getStdDev()
        );
    }

}
