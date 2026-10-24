import { useState, type FormEvent } from 'react';
import type { StoneCreateRequest } from '../../../../api/dto/StoneCreateRequest';
import type { StoneSize } from '../../../../enums/StoneSize';
import type { StoneType } from '../../../../enums/StoneType';
import { stoneSizeLabels, stoneTypeLabels } from '../../../../domain/stone/presentation/stoneLabels';
import { initialStoneForm, toStoneCreateRequest, validateStoneForm, type StoneFormErrors } from '../../validation/stoneForm';
import './AddStoneForm.css';
import { usePhotoDrafts } from '../../hooks/usePhotoDrafts';
import { PhotoDraftGallery } from '../PhotoDraftGallery/PhotoDraftGallery';

interface AddStoneFormProps {
  onSubmit: (request: StoneCreateRequest) => void;
  submitting?: boolean;
}

export function AddStoneForm({ onSubmit, submitting = false }: AddStoneFormProps) {
  const [values, setValues] = useState(initialStoneForm);
  const [errors, setErrors] = useState<StoneFormErrors>({});
  const { photos, uploading, error, selectFiles, removePhoto, isUploading } = usePhotoDrafts();

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || isUploading()) return;
    const errors = validateStoneForm(values);
    setErrors(errors);
    const firstInvalid = Object.keys(errors)[0];
    if (firstInvalid) {
      const control = event.currentTarget.elements.namedItem(firstInvalid);
      if (control instanceof HTMLElement) control.focus();
      return;
    }
    onSubmit(toStoneCreateRequest(values, photos.map(photo => photo.id)));
  }

  const fieldAccessibility = (field: keyof typeof values) => ({
    'aria-invalid': Boolean(errors[field]),
    'aria-describedby': errors[field] ? `${field}-error` : undefined,
  });
  const fieldError = (field: keyof typeof values) => errors[field]
    ? <p className="add-stone-error" id={`${field}-error`} role="alert">{errors[field]}</p> : null;

  return (
    <form className="add-stone-form" aria-label="Stone details" noValidate onSubmit={submit} aria-busy={submitting}>
      <PhotoDraftGallery photos={photos} uploading={uploading} disabled={submitting} error={error}
        onSelectFiles={files => { if (!submitting) void selectFiles(files); }}
        onRemovePhoto={removePhoto} />
      <fieldset className="add-stone-fields" disabled={submitting}>
        <div className="add-stone-field">
          <label htmlFor="stone-name">Name *</label>
          <input id="stone-name" name="name" required maxLength={120} value={values.name}
            {...fieldAccessibility('name')} onChange={event => setValues({ ...values, name: event.target.value })} />
          {fieldError('name')}
        </div>
        <div className="add-stone-choices">
          <div className="add-stone-field">
            <label htmlFor="stone-type">Stone type *</label>
            <select id="stone-type" name="stoneType" required value={values.stoneType}
              {...fieldAccessibility('stoneType')} onChange={event => setValues({ ...values, stoneType: event.target.value as StoneType })}>
              <option value="">Choose a type</option>
              {Object.entries(stoneTypeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            {fieldError('stoneType')}
          </div>
          <div className="add-stone-field">
            <label htmlFor="stone-size">Size *</label>
            <select id="stone-size" name="stoneSize" required value={values.stoneSize}
              {...fieldAccessibility('stoneSize')} onChange={event => setValues({ ...values, stoneSize: event.target.value as StoneSize })}>
              <option value="">Choose a size</option>
              {Object.entries(stoneSizeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
            {fieldError('stoneSize')}
          </div>
        </div>
        <div className="add-stone-field">
          <label htmlFor="stone-biography">Biography (optional)</label>
          <textarea id="stone-biography" name="biography" rows={5} maxLength={2048} value={values.biography}
            {...fieldAccessibility('biography')} onChange={event => setValues({ ...values, biography: event.target.value })} />
          {fieldError('biography')}
        </div>
        <button className="add-stone-primary" type="submit" disabled={submitting || uploading}>{submitting ? 'Adding stone…' : 'Add stone'}</button>
      </fieldset>
      <p className="add-stone-progress" role="status" aria-label="Stone creation progress">{submitting ? 'Adding stone…' : ''}</p>
    </form>
  );
}
