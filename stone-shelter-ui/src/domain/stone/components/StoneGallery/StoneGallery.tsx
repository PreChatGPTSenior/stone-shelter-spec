import { useCallback, useEffect, useRef, useState } from 'react';
import type { SyntheticEvent } from 'react';
import type { StonePhotoResponse } from '../../../../api/dto/StonePhotoResponse';
import './StoneGallery.css';

const placeholder = '/placeholder-rock.png';

function usePlaceholder(event: SyntheticEvent<HTMLImageElement>) {
  if (event.currentTarget.getAttribute('src') !== placeholder) {
    event.currentTarget.src = placeholder;
  }
}

export function StoneGallery({ name, photos, photo }: {
  name: string;
  photos: StonePhotoResponse[];
  photo?: string | null;
}) {
  const [selected, setSelected] = useState(0);
  const [bounds, setBounds] = useState({ overflow: false, previous: false, next: false });
  const stripRef = useRef<HTMLDivElement>(null);
  const thumbnailRefs = useRef<(HTMLButtonElement | null)[]>([]);
  const selectedPhoto = photos[selected] ?? photos[0];

  const measure = useCallback(() => {
    const strip = stripRef.current;
    if (!strip) return;
    const maximum = Math.max(0, strip.scrollWidth - strip.clientWidth);
    const availableWidth = strip.parentElement?.clientWidth || strip.clientWidth;
    const nextBounds = {
      overflow: strip.scrollWidth - availableWidth > 1,
      previous: strip.scrollLeft > 1,
      next: strip.scrollLeft < maximum - 1,
    };
    setBounds(previous => previous.overflow === nextBounds.overflow
      && previous.previous === nextBounds.previous && previous.next === nextBounds.next
      ? previous : nextBounds);
  }, []);

  useEffect(() => {
    const strip = stripRef.current;
    if (!strip) return;
    measure();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(strip);
    if (strip.parentElement) observer?.observe(strip.parentElement);
    window.addEventListener('resize', measure);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, [measure, photos.length]);

  function scrollStrip(left: number) {
    const strip = stripRef.current;
    if (!strip) return;
    const target = Math.max(0, Math.min(left, strip.scrollWidth - strip.clientWidth));
    if (typeof strip.scrollTo === 'function') {
      strip.scrollTo({ left: target, behavior: 'instant' });
    } else {
      strip.scrollLeft = target;
    }
    measure();
  }

  function selectPhoto(index: number) {
    setSelected(index);
    const strip = stripRef.current;
    const thumbnail = thumbnailRefs.current[index];
    if (!strip || !thumbnail) return;
    if (thumbnail.offsetLeft < strip.scrollLeft) {
      scrollStrip(thumbnail.offsetLeft);
    } else if (thumbnail.offsetLeft + thumbnail.offsetWidth > strip.scrollLeft + strip.clientWidth) {
      scrollStrip(thumbnail.offsetLeft + thumbnail.offsetWidth - strip.clientWidth);
    }
  }

  function moveStrip(direction: number) {
    const strip = stripRef.current;
    if (strip) scrollStrip(strip.scrollLeft + direction * strip.clientWidth);
  }

  return (
    <section className="stone-gallery" aria-label={`${name} photo gallery`}>
      <img
        key={selectedPhoto ? selectedPhoto.id : 'legacy'}
        className="stone-gallery-main"
        src={selectedPhoto?.url || photo || placeholder}
        alt={selectedPhoto ? `${name}, photo ${photos.indexOf(selectedPhoto) + 1} of ${photos.length}`
          : photo ? name : `Photo coming soon for ${name}`}
        onError={usePlaceholder}
      />
      {photos.length > 0 && (
        <div className="stone-gallery-carousel">
          {photos.length > 1 && bounds.overflow && (
            <button
              className="stone-gallery-arrow"
              type="button"
              aria-label="Previous photos"
              disabled={!bounds.previous}
              onClick={() => moveStrip(-1)}
            ><span aria-hidden="true">←</span></button>
          )}
          <div className="stone-gallery-strip" role="group" aria-label="Photo thumbnails" ref={stripRef} onScroll={measure}>
            {photos.map((image, index) => (
              <button
                className="stone-gallery-thumbnail"
                key={image.id}
                ref={element => { thumbnailRefs.current[index] = element; }}
                type="button"
                aria-label={`View photo ${index + 1} of ${name}`}
                aria-pressed={selectedPhoto?.id === image.id}
                onClick={() => selectPhoto(index)}
                onKeyDown={event => {
                  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
                  event.preventDefault();
                  const next = Math.min(photos.length - 1, Math.max(0, index + (event.key === 'ArrowRight' ? 1 : -1)));
                  selectPhoto(next);
                  thumbnailRefs.current[next]?.focus({ preventScroll: true });
                }}
              >
                <img src={image.url || placeholder} alt="" onError={usePlaceholder} />
              </button>
            ))}
          </div>
          {photos.length > 1 && bounds.overflow && (
            <button
              className="stone-gallery-arrow"
              type="button"
              aria-label="Next photos"
              disabled={!bounds.next}
              onClick={() => moveStrip(1)}
            ><span aria-hidden="true">→</span></button>
          )}
        </div>
      )}
    </section>
  );
}
