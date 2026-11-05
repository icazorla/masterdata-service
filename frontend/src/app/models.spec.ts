import { describe, expect, it } from 'vitest';

import { CatalogType, newDraft, toPayload } from './models';

const province = {
  type: 'province',
  name: 'Province',
  fields: [{ name: 'regionCode', dataType: 'integer', required: true, readOnly: false, translatable: false, sortable: true, extra: true, filterOperators: [] }],
} as CatalogType;

describe('toPayload', () => {
  it('drops empty locales and empty extras, flattens extras', () => {
    const d = newDraft(province);
    d.translations['en'].name = ' Sevilla ';
    d.extras['regionCode'] = 1;
    expect(toPayload(d)).toEqual({ weight: 0, enabled: true, translations: { en: { name: 'Sevilla', description: null } }, regionCode: 1 });
    d.extras['regionCode'] = null;
    expect(toPayload(d)).not.toHaveProperty('regionCode');
  });
});
