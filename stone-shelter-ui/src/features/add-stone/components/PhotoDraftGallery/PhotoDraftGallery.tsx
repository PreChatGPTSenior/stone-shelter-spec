import type { SyntheticEvent } from 'react';
import type { StonePhotoDraftUploadResponse } from '../../../../api/dto/StonePhotoDraftUploadResponse';
import { maximumPhotos } from '../../validation/photoFiles';
import './PhotoDraftGallery.css';

interface PhotoDraftGalleryProps {
  photos: StonePhotoDraftUploadResponse[];
  uploading: boolean;
  disabled: boolean;
  error?: string;
  onSelectFiles: (files: File[]) => void;
  onRemovePhoto: (id: string) => void;
}

function usePlaceholder(event: SyntheticEvent<HTMLImageElement>) {
  if (event.currentTarget.getAttribute('src') !== '/placeholder-rock.png') event.currentTarget.src = '/placeholder-rock.png';
}

export function PhotoDraftGallery({ photos, uploading, disabled, error, onSelectFiles, onRemovePhoto }: PhotoDraftGalleryProps) {
  return (
    <section className="add-stone-preview" aria-label="Photo preview" aria-busy={uploading}>
      <ol className="photo-draft-grid" aria-label="Photo slots">
        {Array.from({ length: maximumPhotos }, (_, index) => {
          const photo = photos[index];
          return <li key={photo?.id ?? `empty-${index}`}
            className={`photo-draft-slot${photo ? '' : ' photo-draft-slot-empty'}`}
            aria-label={photo ? `Photo ${index + 1}` : `Empty photo slot ${index + 1}`}>
            {photo ? <>
              <figure>
                <img src={photo.url} alt={`Photo ${index + 1} preview`} onError={usePlaceholder} />
                <figcaption>{index === 0 ? 'Cover' : `Photo ${index + 1}`}</figcaption>
              </figure>
              <button type="button" aria-label={`Remove photo ${index + 1}`} title={`Remove photo ${index + 1}`}
                disabled={disabled || uploading} onClick={() => onRemovePhoto(photo.id)}><span aria-hidden="true">×</span></button>
            </> : null}
          </li>;
        })}
      </ol>
      <h2>Photos (optional)</h2>
      <p>{photos.length ? 'The first photo is the cover.' : 'No photos selected. Your stone will use the default image.'}</p>
      <p className="photo-draft-count" aria-live="polite">{photos.length} / {maximumPhotos} photos</p>
      <label className="photo-draft-label" htmlFor="stone-photos">Choose photos</label>
      <input id="stone-photos" name="photos" type="file" multiple accept="image/jpeg,image/png,image/webp"
        disabled={disabled || uploading} aria-invalid={Boolean(error)} aria-describedby={`photos-help${error ? ' photos-error' : ''}`}
        onChange={event => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          onSelectFiles(files);
        }} />
      <small id="photos-help">JPEG, PNG or WebP. Up to 16 photos, 10 MiB each.</small>
      {error && <p className="add-stone-error" id="photos-error" role="alert">{error}</p>}
      <p className="photo-draft-progress" role="status" aria-label="Photo upload progress">{uploading ? 'Uploading photos…' : ''}</p>
    </section>
  );
}
