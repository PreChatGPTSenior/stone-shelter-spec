import { useEffect, useRef } from 'react';
import { StoneGallery } from '../../../../domain/stone/components/StoneGallery/StoneGallery';
import { adoptionStatusLabels, stoneSizeLabels, stoneTypeLabels } from '../../../../domain/stone/presentation/stoneLabels';
import { useStoneDetails } from '../../hooks/useStoneDetails';
import { AdoptStoneAction } from '../../../adopt-stone/components/AdoptStoneAction/AdoptStoneAction';
import './StoneDetailsPage.css';
import { catalogPath } from '../../../../App/navigation/pageRoutes';

const dateFormat = new Intl.DateTimeFormat('en-US', {
  day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC',
});

export function StoneDetailsPage({ id, onCatalogChange }: { id?: number; onCatalogChange?: () => void }) {
  const { stone, loading, error, refreshStone } = useStoneDetails(id);
  const heading = useRef<HTMLHeadingElement>(null);
  const pageReady = !stone && !loading;

  useEffect(() => {
    if (stone || pageReady) heading.current?.focus({ preventScroll: true });
  }, [id, stone?.id, pageReady]);

  return (
    <main className="stone-details-page" aria-labelledby="stone-details-heading">
      <nav className="stone-details-navigation" aria-label="Stone navigation">
        <a href={catalogPath}><span aria-hidden="true">←</span> Back to catalog</a>
        {stone && <><span aria-hidden="true">/</span><span aria-current="page">{stone.name}</span></>}
      </nav>
      {loading && !stone && <div><h1 id="stone-details-heading" ref={heading} tabIndex={-1}>Stone details</h1><p role="status">Loading stone…</p></div>}
      {error && <div>{!stone && <h1 id="stone-details-heading" ref={heading} tabIndex={-1}>Unable to load stone</h1>}<p role="alert">{error}</p><button type="button" onClick={refreshStone}>Retry stone</button></div>}
      {stone ? <div className="stone-details-grid">
        <StoneGallery key={stone.id} name={stone.name} photos={stone.photos} photo={stone.photo} />
        <div className="stone-details-information">
          <div className="stone-details-identity">
            <h1 id="stone-details-heading" ref={heading} tabIndex={-1}>{stone.name}</h1>
            <p className="stone-details-id">ID {stone.id}</p>
            <span className="stone-status">{adoptionStatusLabels[stone.adoptionStatus]}</span>
          </div>
          <AdoptStoneAction key={stone.id} stone={stone} onReserved={() => { refreshStone(); onCatalogChange?.(); }} />
          <section className="stone-details-characteristics" aria-labelledby="stone-characteristics-heading">
            <h2 id="stone-characteristics-heading">Characteristics</h2>
            <dl>
              <div><dt>Type</dt><dd>{stoneTypeLabels[stone.stoneType]}</dd></div>
              <div><dt>Size</dt><dd>{stoneSizeLabels[stone.stoneSize]}</dd></div>
              <div><dt>Admission date</dt><dd><time dateTime={stone.admissionDate}>{dateFormat.format(new Date(stone.admissionDate))}</time></dd></div>
            </dl>
          </section>
          <section className="stone-details-biography" aria-labelledby="stone-biography-heading">
            <h2 id="stone-biography-heading">Biography</h2>
            <p>{stone.biography?.trim() ? stone.biography : 'This stone’s story is coming soon.'}</p>
          </section>
        </div>
      </div> : !loading && !error && <div className="stone-details-not-found">
        <h1 id="stone-details-heading" ref={heading} tabIndex={-1}>Stone not found</h1>
        <p>We could not find a stone at this address. Return to the catalog to meet our stones.</p>
      </div>}
    </main>
  );
}
