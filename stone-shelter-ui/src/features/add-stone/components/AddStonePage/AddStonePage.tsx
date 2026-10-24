import { useLayoutEffect, useRef } from 'react';
import { StoneGallery } from '../../../../domain/stone/components/StoneGallery/StoneGallery';
import { useStoneCreation } from '../../hooks/useStoneCreation';
import { AddStoneForm } from '../AddStoneForm/AddStoneForm';
import './AddStonePage.css';
import { catalogPath, stoneDetailsPath } from '../../../../App/navigation/pageRoutes';

export function AddStonePage({ onCreated }: { onCreated: () => void }) {
  const { result, submitting, error, submit, addAnother } = useStoneCreation(onCreated);
  const heading = useRef<HTMLHeadingElement>(null);
  const confirmation = useRef<HTMLHeadingElement>(null);

  useLayoutEffect(() => {
    (result ? confirmation : heading).current?.focus({ preventScroll: true });
  }, [result]);

  return (
    <main className="add-stone-page" aria-labelledby="add-stone-heading">
      <nav className="add-stone-navigation" aria-label="Stone navigation"><a href={catalogPath}><span aria-hidden="true">←</span> Back to catalog</a></nav>
      <h1 id="add-stone-heading" ref={heading} tabIndex={-1}>Add a stone</h1>
      {error && <p className="add-stone-error" role="alert">{error}</p>}
      {result ? <section className="add-stone-success" aria-label="Created stone">
        <StoneGallery name={result.name} photos={result.photos} photo={result.photo} />
        <div className="add-stone-confirmation">
          <h2 ref={confirmation} tabIndex={-1} role="status">Stone added successfully.</h2>
          <p className="add-stone-name">{result.name}</p>
          <p className="add-stone-id">ID {result.id}</p>
          <div className="add-stone-result-actions">
            <a className="add-stone-primary" href={stoneDetailsPath(result.id)}>View stone</a>
            <button className="add-stone-secondary" type="button" onClick={addAnother}>Add another stone</button>
          </div>
        </div>
      </section> : <AddStoneForm submitting={submitting} onSubmit={request => { void submit(request); }} />}
    </main>
  );
}
