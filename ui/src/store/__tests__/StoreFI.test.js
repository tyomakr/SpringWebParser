// Мокаем сервис, чтобы не подхватывать реальный axios
jest.mock('../../service/backendApiService', () => ({
    getWebImagesOnPages: jest.fn(),
    saveAndPublishSelectedImages: jest.fn().mockResolvedValue('OK'),
}));

import { StoreFI } from '../StoreFI';

describe('StoreFI', () => {
    let store;

    beforeEach(() => {
        // Новый экземпляр перед каждым тестом
        store = new StoreFI();
        store.selectedImages = [
            { url: 'a' },
            { url: 'b' },
            { url: 'c' },
            { url: 'b' },
        ];
    });

    test('reorderSelectedImages корректно меняет порядок', () => {
        store.reorderSelectedImages(0, 2);
        expect(store.selectedImages.map(i => i.url))
            .toEqual(['b', 'c', 'a', 'b']);
    });

    test('фильтрует дубликаты по url, сохраняя первый', () => {
        const unique = store.selectedImages.filter(
            (img, idx, arr) => arr.findIndex(x => x.url === img.url) === idx
        );
        expect(unique.map(i => i.url)).toEqual(['a', 'b', 'c']);
    });
});