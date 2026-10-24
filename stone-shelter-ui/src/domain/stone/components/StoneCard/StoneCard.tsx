import type { StoneSearchResponse } from '../../../../api/dto/StoneSearchResponse';
import { stoneSizeLabels, stoneTypeLabels } from '../../presentation/stoneLabels';
import './StoneCard.css';
import { stoneDetailsPath } from '../../../../App/navigation/pageRoutes';

const placeholder = '/placeholder-rock.png';

export function StoneCard({ stone }: { stone: StoneSearchResponse }) {
  return (
    <article className="stone-card" aria-labelledby={`stone-${stone.id}`}>
      <a className="stone-photo-link" href={stoneDetailsPath(stone.id)} aria-label={`View details for ${stone.name}`}>
        <img
        className="stone-photo"
        src={stone.photo || placeholder}
        alt={stone.photo ? stone.name : `Photo coming soon for ${stone.name}`}
        loading="lazy"
        onError={(event) => {
          if (event.currentTarget.getAttribute('src') !== placeholder) {
            event.currentTarget.src = placeholder;
          }
        }}
        />
      </a>
      <div className="stone-content">
        <h2 id={`stone-${stone.id}`}>{stone.name}</h2>
        <dl className="stone-facts">
          <div><dt>Type</dt><dd>{stoneTypeLabels[stone.stoneType]}</dd></div>
          <div><dt>Size</dt><dd>{stoneSizeLabels[stone.stoneSize]}</dd></div>
        </dl>
        <p className="stone-biography">{stone.biography || 'This stone’s story is coming soon.'}</p>
      </div>
    </article>
  );
}
