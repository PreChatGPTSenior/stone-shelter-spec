export const maximumPhotos = 16;
export const maximumPhotoBytes = 10 * 1024 * 1024;
const supportedTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);

export function validatePhotoFiles(files: File[], existingCount: number): string | undefined {
  if (existingCount + files.length > maximumPhotos) return 'Choose at most 16 photos in total.';
  for (const file of files) {
    if (!supportedTypes.has(file.type)) return 'Choose JPEG, PNG or WebP photos.';
    if (file.size === 0) return 'Photos must not be empty.';
    if (file.size > maximumPhotoBytes) return 'Each photo must be 10 MiB or smaller.';
  }
}
