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
} from "@mui/material";
import vkHistoryService from "../service/vkHistoryService";

export default function VkHistoryTrainingPage() {
  const [onlyTraining, setOnlyTraining] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [rows, setRows] = useState([]);

  const load = async (trainingOnly) => {
    setLoading(true);
    setError(null);
    try {
      const res = trainingOnly
        ? await vkHistoryService.training()
        : await vkHistoryService.entries();
      setRows(res.data || []);
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
    <Container sx={{ py: 4 }}>
      <Typography variant="h4" gutterBottom>
        История VK / Обучающий датасет
      </Typography>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
          <FormControlLabel
            control={
              <Switch
                checked={onlyTraining}
                onChange={(e) => setOnlyTraining(e.target.checked)}
              />
            }
            label="Показывать только записи для обучения"
          />
          <Button variant="outlined" onClick={() => load(onlyTraining)} disabled={loading}>
            Обновить
          </Button>
          <Typography variant="body2" sx={{ ml: "auto" }}>
            Всего: {totals.total} · Для обучения: {totals.training}
          </Typography>
        </Box>
      </Paper>

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
  );
}
