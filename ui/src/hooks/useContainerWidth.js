import { useState, useEffect, useRef } from 'react';

export function useContainerWidth() {
    const ref = useRef(null);
    const [width, setWidth] = useState(0);

    useEffect(() => {
        const update = () => {
            if (ref.current) {
                setWidth(ref.current.getBoundingClientRect().width);
            }
        };
        update();
        window.addEventListener('resize', update);
        return () => window.removeEventListener('resize', update);
    }, []);

    return [ref, width];
}
