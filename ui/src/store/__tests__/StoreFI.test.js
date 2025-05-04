// 1. Мокаем backendApiService — вручную, чтобы не подгружать настоящий модуль и его ESM-импорты
jest.mock('../../service/backendApiService', () => ({
    getWebImagesOnPages: jest.fn(),
    saveAndPublishSelectedImages: jest.fn()
}));

// 2. Мокаем toast.error из react-toastify
jest.mock('react-toastify', () => ({
    toast: { error: jest.fn() }
}));

// 3. Дальше уже require нужных модулей
const { StoreFI } = require('../StoreFI');
const backendApiService = require('../../service/backendApiService');
const { toast } = require('react-toastify');

describe('StoreFI — шаг 1: getWebImagesFromPages', () => {
    let store;

    beforeEach(() => {
        // Новый экземпляр перед каждым тестом
        store = new StoreFI();

        // Сброс моков
        backendApiService.getWebImagesOnPages.mockReset();
        toast.error.mockClear();
    });

    it('должен загрузить массив и выставить step1=true при успешном ответе', async () => {
        const fake = ['a', 'b', 'c'];
        // Мокаем так, чтобы промис резолвился
        backendApiService.getWebImagesOnPages.mockResolvedValue({ data: fake });

        await store.getWebImagesFromPages(2, 4);

        expect(store.webImages).toEqual(fake);
        expect(store.step1).toBe(true);
        // toast.error не должен был вызываться
        expect(toast.error).not.toHaveBeenCalled();
    });

    it('должен оставить webImages пустым и step1=false при ошибке, и вызвать toast.error', async () => {
        const err = new Error('network down');
        // Мокаем так, чтобы промис реджектился
        backendApiService.getWebImagesOnPages.mockRejectedValue(err);

        await store.getWebImagesFromPages(1, 3);

        expect(store.webImages).toEqual([]);      // ничего не загрузилось
        expect(store.step1).toBe(false);          // флаг не поднялся
        // toast.error вызван с правильным сообщением и опциями
        expect(toast.error).toHaveBeenCalledWith(
            `Не удалось загрузить изображения: ${err.message}`,
            expect.objectContaining({ position: 'bottom-right' })
        );
    });
});