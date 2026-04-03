package com.aypak.alarmengine.nodes;

import com.aypak.alarmengine.core.AlarmEvent;
import com.aypak.flowengine.core.FlowContext;
import com.aypak.flowengine.core.FlowEvent;
import com.aypak.flowengine.core.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分析节点 - 业务逻辑分析。
 * 计算告警严重度、进行关联分析等。
 * Analysis node - business logic analysis.
 * Calculates alarm severity, performs correlation analysis, etc.
 */
public class AnalysisNode implements FlowNode<String, AlarmEvent> {

    private static final Logger log = LoggerFactory.getLogger(AnalysisNode.class);

    /** 是否启用严重度提升规则 / Whether severity boost rule is enabled */
    private volatile boolean enableSeverityBoost = true;

    @Override
    public String getNodeName() {
        return "Analysis";
    }

    @Override
    public boolean process(FlowEvent<String, AlarmEvent> event, FlowContext context) throws Exception {
        long startTime = System.currentTimeMillis();
        AlarmEvent alarmEvent = event.getPayload();

        try {
            // 1. 计算告警严重度 / Calculate alarm severity
            calculateSeverity(alarmEvent, context);

            // 2. 关联分析（可扩展）/ Correlation analysis (extensible)
            correlateAlarm(alarmEvent, context);

            // 3. 丰富告警数据 / Enrich alarm data
            enrichAlarm(alarmEvent, context);

            log.debug("Analysis node processed alarm {} in {}ms, severity: {}",
                    alarmEvent.getId(), System.currentTimeMillis() - startTime, alarmEvent.getSeverity());

            return true;

        } catch (Exception e) {
            log.error("Analysis failed for alarm {}", alarmEvent.getId(), e);
            throw e;
        }
    }

    /**
     * 计算告警严重度。
     * 可根据业务规则调整严重度级别。
     * Calculate alarm severity.
     * Can adjust severity level based on business rules.
     */
    private void calculateSeverity(AlarmEvent event, FlowContext context) {
        if (!enableSeverityBoost) {
            return;
        }

        // 示例：根据 payload 中的指标提升严重度
        // Example: Boost severity based on criticality in payload
        String criticality = event.getPayload("criticality");
        if ("critical".equalsIgnoreCase(criticality)) {
            event.setSeverity(AlarmEvent.Severity.CRITICAL);
            log.debug("Boosted alarm {} to CRITICAL severity", event.getId());
        }

        // 示例：根据告警类型设置默认严重度
        // Example: Set default severity based on alarm type
        if (event.getSeverity() == null) {
            String alarmType = event.getAlarmType();
            if (alarmType != null) {
                if (alarmType.contains("CRITICAL") || alarmType.contains("FATAL")) {
                    event.setSeverity(AlarmEvent.Severity.CRITICAL);
                } else if (alarmType.contains("MAJOR") || alarmType.contains("HIGH")) {
                    event.setSeverity(AlarmEvent.Severity.MAJOR);
                } else if (alarmType.contains("MINOR") || alarmType.contains("MEDIUM")) {
                    event.setSeverity(AlarmEvent.Severity.MINOR);
                } else if (alarmType.contains("WARNING") || alarmType.contains("WARN")) {
                    event.setSeverity(AlarmEvent.Severity.WARNING);
                } else {
                    event.setSeverity(AlarmEvent.Severity.INFO);
                }
            }
        }
    }

    /**
     * 关联分析。
     * 可扩展实现告警关联、根因分析等。
     * Correlation analysis.
     * Can be extended to implement alarm correlation, root cause analysis, etc.
     */
    private void correlateAlarm(AlarmEvent event, FlowContext context) {
        // 预留扩展点 / Reserved extension point
        // 可以实现：/ Can implement:
        // - 相同时段同一设备的告警关联 / Alarm correlation for same device in same time period
        // - 告警风暴检测 / Alarm storm detection
        // - 根因分析 / Root cause analysis
    }

    /**
     * 丰富告警数据。
     * 添加额外的上下文信息。
     * Enrich alarm data.
     * Add additional context information.
     */
    private void enrichAlarm(AlarmEvent event, FlowContext context) {
        // 预留扩展点 / Reserved extension point
        // 可以实现：/ Can implement:
        // - 添加设备信息 / Add device information
        // - 添加地理位置 / Add geographic location
        // - 添加业务影响分析 / Add business impact analysis
    }

    /**
     * 设置是否启用严重度提升。
     * Set whether severity boost is enabled.
     */
    public void setEnableSeverityBoost(boolean enableSeverityBoost) {
        this.enableSeverityBoost = enableSeverityBoost;
    }

    @Override
    public void onFailure(FlowEvent<String, AlarmEvent> event, Throwable error) {
        log.error("AnalysisNode failed for alarm {}: {}", event.getPayload().getId(), error.getMessage());
    }

    /**
     * 分析节点是关键节点。
     * Analysis node is critical.
     */
    @Override
    public boolean isCritical() {
        return true;
    }
}
