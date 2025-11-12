from app.index import BKIndex


def test_bkindex_insert_and_search():
    index = BKIndex()
    index.add(0x0, {"id": "zero"})
    index.bulk_add([
        (0xF0F0F0F0F0F0F0F0, {"id": "f"}),
        (0xFFFFFFFFFFFFFFFF, {"id": "ones"}),
    ])

    assert index.size() == 3

    result = index.nearest(0x0, 2)
    assert result is not None
    assert result[0] == 0
    assert result[1]["id"] == "zero"

    distant = index.nearest(0x0, 1)
    assert distant is not None
    assert distant[0] <= 2


def test_bkindex_miss_when_dist_too_small():
    index = BKIndex()
    index.add(0xFFFF, {"id": "big"})
    assert index.nearest(0x0, 1) is None
