import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import type { StonePhotoResponse } from '../../../../api/dto/StonePhotoResponse';
import { StoneGallery } from './StoneGallery';

const photos: StonePhotoResponse[] = Array.from({ length: 6 }, (_, index) => ({
  id: index + 1,
  url: `/photo-${index + 1}.png`,
  addedAt: `2026-10-06T10:0${index}:00Z`,
  position: index,
}));

let resize = () => {};

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback: () => void) { resize = callback; }
    observe() {}
    disconnect() {}
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function setStripBounds(clientWidth: number, scrollWidth: number, availableWidth = clientWidth) {
  const strip = screen.getByRole('group', { name: 'Photo thumbnails' });
  Object.defineProperties(strip, {
    clientWidth: { configurable: true, value: clientWidth },
    scrollWidth: { configurable: true, value: scrollWidth },
  });
  Object.defineProperty(strip.parentElement!, 'clientWidth', { configurable: true, value: availableWidth });
  act(resize);
  return strip;
}

test('selects a photo without changing persisted order and resets on a new visit', () => {
  const { unmount } = render(<StoneGallery name="Mars" photos={photos} />);
  expect(screen.getByAltText('Mars, photo 1 of 6').getAttribute('src')).toBe('/photo-1.png');
  expect(screen.getByRole('button', { name: 'View photo 1 of Mars' }).getAttribute('aria-pressed')).toBe('true');
  fireEvent.click(screen.getByRole('button', { name: 'View photo 4 of Mars' }));
  expect(screen.getByAltText('Mars, photo 4 of 6').getAttribute('src')).toBe('/photo-4.png');
  expect(screen.getByRole('button', { name: 'View photo 1 of Mars' }).getAttribute('aria-pressed')).toBe('false');
  expect(screen.getByRole('button', { name: 'View photo 4 of Mars' }).getAttribute('aria-pressed')).toBe('true');
  expect(photos.map(photo => photo.id)).toEqual([1, 2, 3, 4, 5, 6]);
  unmount();
  render(<StoneGallery name="Mars" photos={photos} />);
  expect(screen.getByAltText('Mars, photo 1 of 6')).toBeTruthy();
});

test('shows legacy or placeholder without fabricating thumbnails for empty galleries', () => {
  const { rerender } = render(<StoneGallery name="Mars" photos={[]} />);
  expect(screen.getByAltText('Photo coming soon for Mars').getAttribute('src')).toBe('/placeholder-rock.png');
  expect(screen.queryAllByRole('button')).toHaveLength(0);
  rerender(<StoneGallery name="Mars" photos={[]} photo="/legacy.png" />);
  expect(screen.getByAltText('Mars').getAttribute('src')).toBe('/legacy.png');
  expect(screen.queryByRole('group')).toBeNull();
});

test('one photo has a selected thumbnail without unnecessary carousel controls', () => {
  render(<StoneGallery name="Mars" photos={photos.slice(0, 1)} />);
  setStripBounds(320, 88);
  expect(screen.getAllByRole('button')).toHaveLength(1);
  expect(screen.getByRole('button', { name: 'View photo 1 of Mars' }).getAttribute('aria-pressed')).toBe('true');
  expect(screen.queryByRole('button', { name: 'Previous photos' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Next photos' })).toBeNull();
});

test('finite carousel arrows move only the strip and respect both boundaries', () => {
  render(<StoneGallery name="Mars" photos={photos} />);
  const strip = setStripBounds(200, 600);
  const scrollTo = vi.fn((options: ScrollToOptions) => {
    strip.scrollLeft = options.left ?? 0;
    fireEvent.scroll(strip);
  });
  Object.defineProperty(strip, 'scrollTo', { configurable: true, value: scrollTo });
  const previous = screen.getByRole('button', { name: 'Previous photos' }) as HTMLButtonElement;
  const next = screen.getByRole('button', { name: 'Next photos' }) as HTMLButtonElement;
  expect(previous.disabled).toBe(true);
  expect(next.disabled).toBe(false);
  fireEvent.click(next);
  expect(strip.scrollLeft).toBe(200);
  expect(previous.disabled).toBe(false);
  fireEvent.click(next);
  expect(strip.scrollLeft).toBe(400);
  expect(next.disabled).toBe(true);
  expect(screen.getByAltText('Mars, photo 1 of 6')).toBeTruthy();
  fireEvent.click(previous);
  fireEvent.click(previous);
  expect(strip.scrollLeft).toBe(0);
  expect(previous.disabled).toBe(true);
  expect(scrollTo).toHaveBeenCalledTimes(4);
});

test('touch scrolling and resize update arrow state without resetting the selected photo', () => {
  render(<StoneGallery name="Mars" photos={photos} />);
  const strip = setStripBounds(200, 600);
  fireEvent.click(screen.getByRole('button', { name: 'View photo 3 of Mars' }));
  strip.scrollLeft = 400;
  fireEvent.scroll(strip);
  expect((screen.getByRole('button', { name: 'Next photos' }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByRole('button', { name: 'Previous photos' }) as HTMLButtonElement).disabled).toBe(false);
  strip.scrollLeft = 0;
  setStripBounds(700, 600);
  expect(screen.queryByRole('button', { name: 'Next photos' })).toBeNull();
  expect(screen.getByAltText('Mars, photo 3 of 6')).toBeTruthy();
  setStripBounds(200, 600);
  expect(screen.getByRole('button', { name: 'Next photos' })).toBeTruthy();
});

test('resize removes arrows when thumbnails fit the carousel width without the arrows', () => {
  render(<StoneGallery name="Mars" photos={photos} />);
  setStripBounds(200, 600, 292);
  expect(screen.getByRole('button', { name: 'Next photos' })).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: 'View photo 4 of Mars' }));
  setStripBounds(550, 600, 642);
  expect(screen.queryByRole('button', { name: 'Previous photos' })).toBeNull();
  expect(screen.queryByRole('button', { name: 'Next photos' })).toBeNull();
  expect(screen.getByAltText('Mars, photo 4 of 6')).toBeTruthy();
  setStripBounds(200, 600, 292);
  expect(screen.getByRole('button', { name: 'Next photos' })).toBeTruthy();
});

test('keyboard selection brings an offscreen thumbnail into the strip without moving the page', () => {
  render(<StoneGallery name="Mars" photos={photos} />);
  const strip = setStripBounds(200, 600);
  const third = screen.getByRole('button', { name: 'View photo 3 of Mars' });
  Object.defineProperties(third, {
    offsetLeft: { configurable: true, value: 216 },
    offsetWidth: { configurable: true, value: 84 },
  });
  fireEvent.keyDown(screen.getByRole('button', { name: 'View photo 2 of Mars' }), { key: 'ArrowRight' });
  expect(screen.getByAltText('Mars, photo 3 of 6')).toBeTruthy();
  expect(document.activeElement).toBe(third);
  expect(strip.scrollLeft).toBe(100);
  expect(window.scrollY).toBe(0);
});

test('broken main and thumbnail images fall back once and other photos remain selectable', () => {
  render(<StoneGallery name="Mars" photos={photos} />);
  const main = screen.getByAltText('Mars, photo 1 of 6');
  fireEvent.error(main);
  expect(main.getAttribute('src')).toBe('/placeholder-rock.png');
  const sourceChanges = vi.spyOn(main as HTMLImageElement, 'src', 'set');
  fireEvent.error(main);
  expect(sourceChanges).not.toHaveBeenCalled();
  const thumbnail = screen.getByRole('button', { name: 'View photo 2 of Mars' }).querySelector('img')!;
  fireEvent.error(thumbnail);
  expect(thumbnail.getAttribute('src')).toBe('/placeholder-rock.png');
  fireEvent.click(screen.getByRole('button', { name: 'View photo 2 of Mars' }));
  expect(screen.getByAltText('Mars, photo 2 of 6').getAttribute('src')).toBe('/photo-2.png');
  expect(screen.getAllByRole('button', { name: /^View photo/ })).toHaveLength(6);
});
