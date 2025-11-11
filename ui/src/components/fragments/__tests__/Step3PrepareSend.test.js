import React from 'react';
import { render, fireEvent } from '@testing-library/react';
import Step3PrepareSend from '../Step3PrepareSend';
import storeFI from '../../../store/storeFI';

// Мокаем LogConsole, чтобы тест не падал
jest.mock('../../LogConsole', () => () => <div data-testid="log" />);

// Мокаем API-сервис, чтобы не тащить реальные ES-модули axios
jest.mock('../../../service/backendApiService', () => ({
    getWebImagesOnPages: jest.fn(),
    saveAndPublishSelectedImages: jest.fn().mockResolvedValue('OK'),
}));

beforeEach(() => {
    // Сбрасываем стор перед каждым тестом
    storeFI.selectedImages = [
        { directLink: '1' },
        { directLink: '2' },
        { directLink: '1' },
    ];
    storeFI.saveAndPublishSelectedImages = jest.fn().mockResolvedValue('OK');
    storeFI.removeSelectedImageByIndex = jest.fn();
    storeFI.reorderSelectedImages = jest.fn();
});

test('при клике «Отправить» дубли удаляются и вызывается нужный метод', () => {
    const { getByText } = render(<Step3PrepareSend storeFI={storeFI} />);
    fireEvent.click(getByText('Отправить'));

    // Ожидаем, что у сторе вызвался метод с уникальными directLink
    expect(storeFI.saveAndPublishSelectedImages).toHaveBeenCalledWith([
        { directLink: '1' },
        { directLink: '2' },
    ]);
});

test('reorderSelectedImages не вызывается сразу при рендере', () => {
    render(<Step3PrepareSend storeFI={storeFI} />);
    expect(storeFI.reorderSelectedImages).not.toHaveBeenCalled();
});