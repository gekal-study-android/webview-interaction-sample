'use client';

import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import TranslateIcon from '@mui/icons-material/Translate';

import { SectionCard } from './section-card';

/** 全角括弧などの描画確認用サンプル（移行前の index.html から引き継ぎ）。 */
const ITEMS = ['[※] First item（項目１）', '[※] Second item（項目２）', '[※] Third item（項目３）'];

export function TextSampleCard() {
  return (
    <SectionCard icon={<TranslateIcon />} title="表示サンプル" subheader="全角文字・記号のレンダリング確認" badge="Web">
      <List dense disablePadding>
        {ITEMS.map((item) => (
          <ListItem key={item} disableGutters sx={{ py: 0.25 }}>
            <ListItemText primary={item} slotProps={{ primary: { variant: 'body2' } }} />
          </ListItem>
        ))}
      </List>
    </SectionCard>
  );
}
