import React from 'react';
import { render, fireEvent } from '@testing-library/react';
import PrepareToSendImages from '../PrepareToSendImages';
import storeFI from '../../../store/StoreFI';

// Мокаем LogConsole, чтобы тест не падал
jest.mock('../../LogConsole', () => () => <div data-testid="log" />);

// Мокаем API-сервис, чтобы Jest не подгружал ES-модули из axios
jest.mock('../../../service/backendApiService', () => ({
    getWebImagesOnPages: jest.fn(),
    saveAndPublishSelectedImages: jest.fn().mockResolvedValue('OK'),
}));

beforeEach(() => {
    // Сбрасываем стор перед каждым тестом
    storeFI.selectedImages = [
        { url: '1' },
        { url: '2' },
        { url: '1' },
    ];
    storeFI.saveAndPublishSelectedImages = jest.fn().mockResolvedValue('OK');
    storeFI.removeSelectedImageByIndex = jest.fn();
    storeFI.reorderSelectedImages = jest.fn();
});

test('при клике «Отправить» дубли удаляются и вызывается нужный метод', async () => {
    const { getByText } = render(<PrepareToSendImages storeFI={storeFI} />);
    fireEvent.click(getByText('Отправить'));

    // Проверяем, что вызвали метод без дубликатов
    expect(storeFI.saveAndPublishSelectedImages)
        .toHaveBeenCalledWith([
            { url: '1' },
            { url: '2' }
        ]);
});

// Пример простого теста для проверки вызова reorderSelectedImages
test('reorderSelectedImages не вызывается сразу при рендере', () => {
    render(<PrepareToSendImages storeFI={storeFI} />);
    expect(storeFI.reorderSelectedImages).not.toHaveBeenCalled();
});