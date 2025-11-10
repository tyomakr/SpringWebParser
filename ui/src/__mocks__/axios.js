const create = jest.fn(() => ({
    post: jest.fn(() => Promise.resolve({ data: {} })),
}));

const axios = {
    create,
};

export default axios;
