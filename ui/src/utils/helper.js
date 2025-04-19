/**
 * Удаляет ANSI‑кодировку (цвета) из строки лога.
 */
export function stripAnsi(line) {
    const ansiPattern = '\\x1b\\[[0-9;]*m';
    const ansiRegex   = new RegExp(ansiPattern, 'g');
    return line.replace(ansiRegex, '');
}
