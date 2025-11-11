import { useState, useEffect } from 'react';

export default function useContainerWidth(ref) {
    const [width, setWidth] = useState(0);

    useEffect(() => {
        if (!ref.current) return;
        // сразу замеряем
        setWidth(ref.current.clientWidth);

        const observer = new ResizeObserver(entries => {
            for (let entry of entries) {
                setWidth(entry.contentRect.width);
            }
        });
        observer.observe(ref.current);
        return () => observer.disconnect();
    }, [ref]);

    return width;
}