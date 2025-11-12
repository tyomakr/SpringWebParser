import React, { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Container,
  FormControlLabel,
  LinearProgress,
  Link as MuiLink,
  Paper,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  useTheme,
  useScrollTrigger,
} from "@mui/material";
import vkHistoryService from "../service/vkHistoryService";

export default function VkHistoryTrainingPage() {
  const [onlyTraining, setOnlyTraining] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [rows, setRows] = useState([]);
  const [lastLoadedAt, setLastLoadedAt] = useState(null);

  const theme = useTheme();
  const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });
  const appBarH = theme.mixins.toolbar?.minHeight || 64;
  const headerH = 64;
  const headerTop = trigger ? 0 : appBarH;

  const formatTimestamp = (date) =>
    date ? date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" }) : "—";

  const load = async (trainingOnly) => {
    setLoading(true);
    setError(null);
    try {
      const res = trainingOnly
        ? await vkHistoryService.training()
        : await vkHistoryService.entries();
      setRows(res.data || []);
      setLastLoadedAt(new Date());
    } catch (e) {
      setError(e?.message || "Ошибка загрузки");
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(onlyTraining);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [onlyTraining]);

  const handleToggle = async (rowIndex) => {
    const row = rows[rowIndex];
    const next = !Boolean(row.useForTraining);

    const prevRows = [...rows];
    const updated = [...rows];
    updated[rowIndex] = { ...row, useForTraining: next };
    setRows(updated);

    try {
      await vkHistoryService.updateUseForTraining(row.id, next);
      if (onlyTraining && !next) {
        load(true);
      }
    } catch (e) {
      setError(e?.message || "Не удалось обновить флаг");
      setRows(prevRows);
    }
  };

  const totals = useMemo(() => {
    const total = rows.length;
    const training = rows.filter((r) => r.useForTraining === true).length;
    return { total, training };
  }, [rows]);

  return (
    <>
      <Box
        sx={{
          position: "fixed",
          top: headerTop,
          left: 0,
          right: 0,
          height: headerH,
          bgcolor: "background.paper",
          borderBottom: 1,
          borderColor: "divider",
          zIndex: theme.zIndex.appBar - 1,
          px: 2,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 2,
          flexWrap: { xs: "wrap", md: "nowrap" },
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, flexWrap: "wrap" }}>
          <FormControlLabel
            control={
              <Switch
                checked={onlyTraining}
                onChange={(e) => setOnlyTraining(e.target.checked)}
              />
            }
            label="Только обучение"
          />
          <Typography variant="body2" color="text.secondary">
            Всего: {totals.total} · Для обучения: {totals.training}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Обновлено: {formatTimestamp(lastLoadedAt)}
          </Typography>
        </Box>
        <Button variant="outlined" onClick={() => load(onlyTraining)} disabled={loading}>
          Обновить
        </Button>
      </Box>

      <Container
        maxWidth={false}
        disableGutters
        sx={{ pt: `${appBarH + headerH}px`, px: 2, pb: 4 }}
      >
        <Typography variant="h4" gutterBottom>
          История VK / Обучающий датасет
        </Typography>

      {loading && <LinearProgress />}

      {error && (
        <Alert severity="error" sx={{ my: 2 }}>
          {error}
        </Alert>
      )}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Превью</TableCell>
              <TableCell>URL</TableCell>
              <TableCell>ML decision</TableCell>
              <TableCell>Score</TableCell>
              <TableCell>Reason</TableCell>
              <TableCell align="center">Use for training</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row, idx) => (
              <TableRow key={row.id ?? `${row.hash}-${idx}`}>
                <TableCell>{row.id ?? "—"}</TableCell>
                <TableCell>
                  <img
                    src={row.url}
                    alt={row.hash}
                    style={{ width: 120, height: 80, objectFit: "cover" }}
                    onError={(e) => (e.currentTarget.style.visibility = "hidden")}
                  />
                </TableCell>
                <TableCell>
                  <MuiLink href={row.url} target="_blank" rel="noreferrer">
                    {row.url}
                  </MuiLink>
                </TableCell>
                <TableCell>{row.mlDecision ?? "—"}</TableCell>
                <TableCell>{row.mlScore != null ? row.mlScore.toFixed(3) : "—"}</TableCell>
                <TableCell>{row.mlReason ?? "—"}</TableCell>
                <TableCell align="center">
                  <Checkbox
                    checked={Boolean(row.useForTraining)}
                    onChange={() => handleToggle(idx)}
                    size="small"
                    inputProps={{ "aria-label": "use-for-training" }}
                  />
                </TableCell>
              </TableRow>
            ))}
            {rows.length === 0 && !loading && !error && (
              <TableRow>
                <TableCell colSpan={7}>
                  <Alert severity="info">Записей нет.</Alert>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      </Container>
    </>
  );
}
