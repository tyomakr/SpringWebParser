import React from "react";
import {
    Alert,
    Box,
    Button,
    Card,
    CardContent,
    Chip,
    CircularProgress,
    Divider,
    Stack,
    TextField,
    Typography,
} from "@mui/material";

import mlPublishService from "../service/mlPublishService";
import vkHistoryService from "../service/vkHistoryService";

const formatInstant = (value) => {
    if (!value) {
        return "—";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value);
    }
    return date.toLocaleString();
};

const formatError = (error) => {
    if (!error) {
        return null;
    }
    return (
        error?.response?.data?.error ||
        error?.response?.data?.message ||
        error?.message ||
        "Unknown error"
    );
};

const MlDiagnosticsPage = () => {
    const [loading, setLoading] = React.useState(false);
    const [mlStatus, setMlStatus] = React.useState(null);
    const [mlConfig, setMlConfig] = React.useState(null);
    const [vkStatus, setVkStatus] = React.useState(null);
    const [ocrDiagnostics, setOcrDiagnostics] = React.useState(null);
    const [ocrRunning, setOcrRunning] = React.useState(false);
    const [ocrLimit, setOcrLimit] = React.useState(50);
    const [ocrOffset, setOcrOffset] = React.useState(0);
    const [errors, setErrors] = React.useState({
        mlStatus: null,
        mlConfig: null,
        vkStatus: null,
        ocrDiagnostics: null,
    });
    const [lastUpdated, setLastUpdated] = React.useState(null);

    const loadAll = React.useCallback(async () => {
        setLoading(true);
        setErrors({ mlStatus: null, mlConfig: null, vkStatus: null, ocrDiagnostics: null });
        const results = await Promise.allSettled([
            mlPublishService.status(),
            mlPublishService.config(),
            vkHistoryService.syncStatus(),
            mlPublishService.ocrDiagnostics(),
        ]);
        const [statusRes, configRes, vkRes, ocrRes] = results;
        if (statusRes.status === "fulfilled") {
            setMlStatus(statusRes.value.data);
        } else {
            setErrors((prev) => ({ ...prev, mlStatus: statusRes.reason }));
        }
        if (configRes.status === "fulfilled") {
            setMlConfig(configRes.value.data);
        } else {
            setErrors((prev) => ({ ...prev, mlConfig: configRes.reason }));
        }
        if (vkRes.status === "fulfilled") {
            setVkStatus(vkRes.value.data);
        } else {
            setErrors((prev) => ({ ...prev, vkStatus: vkRes.reason }));
        }
        if (ocrRes.status === "fulfilled") {
            setOcrDiagnostics(ocrRes.value.data);
        } else {
            setErrors((prev) => ({ ...prev, ocrDiagnostics: ocrRes.reason }));
        }
        setLastUpdated(new Date());
        setLoading(false);
    }, []);

    React.useEffect(() => {
        loadAll();
    }, [loadAll]);

    const runOcrDiagnostics = React.useCallback(async () => {
        setOcrRunning(true);
        setErrors((prev) => ({ ...prev, ocrDiagnostics: null }));
        try {
            const response = await mlPublishService.runOcrDiagnostics(ocrLimit, ocrOffset);
            setOcrDiagnostics(response.data);
        } catch (error) {
            setErrors((prev) => ({ ...prev, ocrDiagnostics: error }));
        } finally {
            setLastUpdated(new Date());
            setOcrRunning(false);
        }
    }, []);

    const host = window.location.hostname || "localhost";
    const metricsUrl = `http://${host}:38000/metrics`;
    const mlConfigUrl = `http://${host}:38000/config`;

    return (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <Typography variant="h5">ML диагностика</Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                    {loading && <CircularProgress size={20} />}
                    <Button variant="contained" onClick={loadAll} disabled={loading}>
                        Обновить
                    </Button>
                </Stack>
            </Box>
            {lastUpdated && (
                <Typography variant="body2" color="text.secondary">
                    Последнее обновление: {lastUpdated.toLocaleString()}
                </Typography>
            )}

            <Card>
                <CardContent>
                    <Stack spacing={1}>
                        <Typography variant="h6">ML status (backend proxy)</Typography>
                        {errors.mlStatus && (
                            <Alert severity="error">
                                {formatError(errors.mlStatus)}
                            </Alert>
                        )}
                        {!errors.mlStatus && !mlStatus && (
                            <Typography color="text.secondary">Данных пока нет.</Typography>
                        )}
                        {mlStatus && (
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                <Chip
                                    label={mlStatus.mlReachable ? "reachable" : "unreachable"}
                                    color={mlStatus.mlReachable ? "success" : "error"}
                                    size="small"
                                />
                                <Typography>indexSize: {mlStatus.indexSize ?? "—"}</Typography>
                                <Typography>
                                    phashMaxDist: {mlStatus.config?.phashMaxDist ?? "—"}
                                </Typography>
                                <Typography>grayBand: {mlStatus.config?.grayBand ?? "—"}</Typography>
                                {mlStatus.error && (
                                    <Typography color="error">error: {mlStatus.error}</Typography>
                                )}
                            </Stack>
                        )}
                    </Stack>
                </CardContent>
            </Card>

            <Card>
                <CardContent>
                    <Stack spacing={1}>
                        <Typography variant="h6">ML client config</Typography>
                        {errors.mlConfig && (
                            <Alert severity="error">
                                {formatError(errors.mlConfig)}
                            </Alert>
                        )}
                        {!errors.mlConfig && !mlConfig && (
                            <Typography color="text.secondary">Данных пока нет.</Typography>
                        )}
                        {mlConfig && (
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                <Chip
                                    label={mlConfig.apiKeyConfigured ? "apiKey configured" : "apiKey missing"}
                                    color={mlConfig.apiKeyConfigured ? "success" : "warning"}
                                    size="small"
                                />
                                <Typography>requireApiKey: {String(mlConfig.requireApiKey)}</Typography>
                                <Typography>maxBatchSize: {mlConfig.maxBatchSize}</Typography>
                                <Typography>
                                    phashMaxDist: {mlConfig.mlServiceConfig?.phashMaxDist ?? "—"}
                                </Typography>
                                <Typography>
                                    grayBand: {mlConfig.mlServiceConfig?.grayBand ?? "—"}
                                </Typography>
                            </Stack>
                        )}
                    </Stack>
                </CardContent>
            </Card>

            <Card>
                <CardContent>
                    <Stack spacing={1}>
                        <Typography variant="h6">VK wall sync status</Typography>
                        {errors.vkStatus && (
                            <Alert severity="error">
                                {formatError(errors.vkStatus)}
                            </Alert>
                        )}
                        {!errors.vkStatus && !vkStatus && (
                            <Typography color="text.secondary">Данных пока нет.</Typography>
                        )}
                        {vkStatus && (
                            <Stack spacing={1}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                                    <Chip
                                        label={vkStatus.running ? "running" : "idle"}
                                        color={vkStatus.running ? "info" : "default"}
                                        size="small"
                                    />
                                    <Typography>lastRun: {formatInstant(vkStatus.lastRun)}</Typography>
                                    <Typography>backoffUntil: {formatInstant(vkStatus.backoffUntil)}</Typography>
                                    <Typography>lastSince: {formatInstant(vkStatus.lastSince)}</Typography>
                                    <Typography>rateLimit: {vkStatus.rateLimit ?? "—"}</Typography>
                                </Stack>
                                {vkStatus.lastError && (
                                    <Alert severity="warning">{vkStatus.lastError}</Alert>
                                )}
                                {vkStatus.lastReport && (
                                    <Stack direction="row" spacing={2} flexWrap="wrap">
                                        <Typography>postsFetched: {vkStatus.lastReport.postsFetched}</Typography>
                                        <Typography>photosFound: {vkStatus.lastReport.photosFound}</Typography>
                                        <Typography>inserted: {vkStatus.lastReport.inserted}</Typography>
                                        <Typography>skipped: {vkStatus.lastReport.skipped}</Typography>
                                    </Stack>
                                )}
                            </Stack>
                        )}
                    </Stack>
                </CardContent>
            </Card>

            <Card>
                <CardContent>
                    <Stack spacing={1}>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                            <Typography variant="h6">OCR diagnostics</Typography>
                            <Button
                                variant="outlined"
                                size="small"
                                onClick={runOcrDiagnostics}
                                disabled={ocrRunning}
                            >
                                Запустить
                            </Button>
                        </Stack>
                        <Stack direction="row" spacing={2} flexWrap="wrap">
                            <TextField
                                label="limit"
                                type="number"
                                size="small"
                                value={ocrLimit}
                                onChange={(event) => setOcrLimit(Number(event.target.value) || 0)}
                                inputProps={{ min: 1 }}
                            />
                            <TextField
                                label="offset"
                                type="number"
                                size="small"
                                value={ocrOffset}
                                onChange={(event) => setOcrOffset(Number(event.target.value) || 0)}
                                inputProps={{ min: 0 }}
                            />
                        </Stack>
                        {errors.ocrDiagnostics && (
                            <Alert severity="error">
                                {formatError(errors.ocrDiagnostics)}
                            </Alert>
                        )}
                        {!errors.ocrDiagnostics && ocrDiagnostics && !ocrDiagnostics.available && (
                            <Alert severity="warning">
                                {ocrDiagnostics.error || "Отчёт не найден. Запусти скрипт диагностики OCR."}
                            </Alert>
                        )}
                        {!errors.ocrDiagnostics && !ocrDiagnostics && (
                            <Typography color="text.secondary">
                                Данных пока нет.
                            </Typography>
                        )}
                        {ocrDiagnostics?.available && ocrDiagnostics.report && (
                            <Stack spacing={1}>
                                <Stack direction="row" spacing={2} flexWrap="wrap">
                                    <Typography>source: {ocrDiagnostics.report.source ?? "unknown"}</Typography>
                                    {ocrDiagnostics.report.inputDir && (
                                        <Typography>inputDir: {ocrDiagnostics.report.inputDir}</Typography>
                                    )}
                                    {ocrDiagnostics.report.limit != null && (
                                        <Typography>limit: {ocrDiagnostics.report.limit}</Typography>
                                    )}
                                    {ocrDiagnostics.report.offset != null && (
                                        <Typography>offset: {ocrDiagnostics.report.offset}</Typography>
                                    )}
                                </Stack>
                                <Stack direction="row" spacing={2} flexWrap="wrap">
                                    <Typography>total: {ocrDiagnostics.report.summary?.total ?? "—"}</Typography>
                                    <Typography>
                                        predictedText: {ocrDiagnostics.report.summary?.predictedTextDominant ?? "—"}
                                    </Typography>
                                    <Typography>kept: {ocrDiagnostics.report.summary?.kept ?? "—"}</Typography>
                                    <Typography>
                                        falsePositive: {ocrDiagnostics.report.summary?.falsePositive ?? "—"}
                                    </Typography>
                                    <Typography>
                                        falseNegative: {ocrDiagnostics.report.summary?.falseNegative ?? "—"}
                                    </Typography>
                                </Stack>
                                <Stack direction="row" spacing={2} flexWrap="wrap">
                                    <Typography>
                                        areaMin: {ocrDiagnostics.report.settings?.textAreaMin ?? "—"}
                                    </Typography>
                                    <Typography>
                                        areaMax: {ocrDiagnostics.report.settings?.textAreaMax ?? "—"}
                                    </Typography>
                                    <Typography>
                                        edgeThr: {ocrDiagnostics.report.settings?.lowDetailThreshold ?? "—"}
                                    </Typography>
                                    <Typography>
                                        stdThr: {ocrDiagnostics.report.settings?.lowDetailStdThreshold ?? "—"}
                                    </Typography>
                                </Stack>
                                {ocrDiagnostics.report.mismatches?.falsePositive?.length > 0 && (
                                    <Typography color="warning.main">
                                        falsePositive: {ocrDiagnostics.report.mismatches.falsePositive.join(", ")}
                                    </Typography>
                                )}
                                {ocrDiagnostics.report.mismatches?.falseNegative?.length > 0 && (
                                    <Typography color="warning.main">
                                        falseNegative: {ocrDiagnostics.report.mismatches.falseNegative.join(", ")}
                                    </Typography>
                                )}
                            </Stack>
                        )}
                    </Stack>
                </CardContent>
            </Card>

            <Card>
                <CardContent>
                    <Stack spacing={1}>
                        <Typography variant="h6">Raw endpoints</Typography>
                        <Divider />
                        <Stack direction="row" spacing={2} flexWrap="wrap">
                            <Button component="a" href="/api/ml/status" target="_blank">
                                /api/ml/status
                            </Button>
                            <Button component="a" href="/api/ml/config" target="_blank">
                                /api/ml/config
                            </Button>
                            <Button component="a" href="/api/ml/ocr-diagnostics" target="_blank">
                                /api/ml/ocr-diagnostics
                            </Button>
                            <Button component="a" href="/api/vk-history/sync-wall/status" target="_blank">
                                /api/vk-history/sync-wall/status
                            </Button>
                            <Button component="a" href={metricsUrl} target="_blank">
                                ml-service /metrics
                            </Button>
                            <Button component="a" href={mlConfigUrl} target="_blank">
                                ml-service /config
                            </Button>
                        </Stack>
                    </Stack>
                </CardContent>
            </Card>
        </Box>
    );
};

export default MlDiagnosticsPage;
