import { useEffect, useRef, useState } from 'react';
import { uploadStonePhotoDraft } from '../../../api/stonesApi';
import type { StonePhotoDraftUploadResponse } from '../../../api/dto/StonePhotoDraftUploadResponse';
import { validatePhotoFiles } from '../validation/photoFiles';
import { ApiError } from '../../../api/ApiError';

export function usePhotoDrafts() {
  const [photos, setPhotos] = useState<StonePhotoDraftUploadResponse[]>([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string>();
  const busy = useRef(false);
  const mounted = useRef(true);
  const generation = useRef(0);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; generation.current += 1; };
  }, []);

  async function selectFiles(files: File[]) {
    if (!files.length || busy.current) return;
    const error = validatePhotoFiles(files, photos.length);
    setError(error);
    if (error) return;
    const currentGeneration = generation.current;
    busy.current = true;
    setUploading(true);
    try {
      const uploads = await Promise.allSettled(files.map(uploadStonePhotoDraft));
      if (mounted.current && generation.current === currentGeneration) {
        setPhotos(current => [...current, ...uploads.flatMap(upload => upload.status === 'fulfilled' ? [upload.value] : [])]);
        const failed = uploads.find(upload => upload.status === 'rejected');
        if (failed?.status === 'rejected') {
          setError(`${failed.reason instanceof ApiError ? failed.reason.message : 'A photo could not be uploaded.'} Successful photos were retained. Select failed photos again to retry.`);
        }
      }
    } finally {
      if (mounted.current && generation.current === currentGeneration) {
        busy.current = false;
        setUploading(false);
      }
    }
  }

  function removePhoto(id: string) {
    if (busy.current) return;
    setPhotos(current => current.filter(photo => photo.id !== id));
    setError(undefined);
  }

  return { photos, uploading, error, selectFiles, removePhoto, isUploading: () => busy.current };
}
