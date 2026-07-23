'use client';

import type { ReactNode } from 'react';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import EmailIcon from '@mui/icons-material/Email';
import LayersIcon from '@mui/icons-material/Layers';
import OpenInBrowserIcon from '@mui/icons-material/OpenInBrowser';
import LinkIcon from '@mui/icons-material/Link';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PlaceIcon from '@mui/icons-material/Place';
import SmsIcon from '@mui/icons-material/Sms';

import { useBridge } from '../bridge-provider';
import { SectionCard } from './section-card';

interface LinkSample {
  icon: ReactNode;
  label: string;
  href: string;
  /** ネイティブ側がどう扱うかの説明。 */
  handledBy: string;
}

const LINKS: LinkSample[] = [
  {
    icon: <EmailIcon />,
    label: 'メールを作成',
    href: 'mailto:support@example.com?subject=WebView%20Interaction%20Sample',
    handledBy: 'ACTION_SENDTO でメールアプリへ',
  },
  {
    icon: <SmsIcon />,
    label: 'SMS を作成',
    href: 'sms:+81312345678?body=WebView%20Interaction%20Sample',
    handledBy: 'ACTION_SENDTO で SMS アプリへ',
  },
  {
    icon: <PlaceIcon />,
    label: '地図で開く',
    href: 'geo:35.681236,139.767125?q=東京駅',
    handledBy: 'ACTION_VIEW で地図アプリへ',
  },
];

/** 外部サイトの表示方法を見比べるためのサンプル URL。 */
const EXTERNAL_URL = 'https://developer.android.com/develop/ui/views/layout/webapps/webview';

export function LinkCard() {
  const { log, hydrated, callNative } = useBridge();

  return (
    <SectionCard
      icon={<LinkIcon />}
      title="リンクの種類"
      subheader="WebView が自身で読み込めないスキームの動作確認"
      badge="Web → Native"
      badgeColor="secondary"
    >
      <Stack spacing={1.5}>
        <List dense disablePadding>
          {LINKS.map((link) => (
            <ListItemButton
              key={link.href}
              component="a"
              href={link.href}
              disableGutters
              sx={{ borderRadius: 2, px: 1 }}
              // 遷移はネイティブ側が横取りするため、Web からは記録だけ行う
              onClick={() =>
                log({
                  direction: 'js-to-native',
                  level: 'info',
                  label: link.href,
                  detail: link.handledBy,
                })
              }
            >
              <ListItemIcon sx={{ minWidth: 36, color: 'primary.main' }}>{link.icon}</ListItemIcon>
              <ListItemText
                primary={link.label}
                secondary={link.handledBy}
                slotProps={{
                  primary: { variant: 'body2', sx: { fontWeight: 600 } },
                  secondary: { variant: 'caption' },
                }}
              />
              <Chip
                size="small"
                variant="outlined"
                label={`${link.href.split(':')[0]}:`}
                sx={{ height: 20, fontSize: 11 }}
              />
            </ListItemButton>
          ))}
        </List>

        <Divider />

        <Stack spacing={1.25}>
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="subtitle2">外部サイトの開き方</Typography>
            <Chip
              size="small"
              variant="outlined"
              icon={<OpenInNewIcon />}
              label={new URL(EXTERNAL_URL).host}
              sx={{ height: 22, fontSize: 11 }}
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            外部サイトは WebView 内には表示しません。2 つの方式を見比べられます。
          </Typography>

          <Button
            variant="contained"
            startIcon={<LayersIcon />}
            onClick={() => callNative('openInAppBrowser', [EXTERNAL_URL])}
            disabled={!hydrated}
            fullWidth
          >
            アプリ内オーバーレイで開く
          </Button>
          <Typography variant="caption" color="text.secondary">
            2 つ目の WebView
            を現在の画面に重ねます。ブラウザに依存せず見た目が一定で、ヘッダーに接続先ホストと閉じるボタンが出ます。
          </Typography>

          <Button
            variant="outlined"
            startIcon={<OpenInBrowserIcon />}
            onClick={() => callNative('openInCustomTab', [EXTERNAL_URL])}
            disabled={!hydrated}
            fullWidth
          >
            Custom Tabs で開く
          </Button>
          <Typography variant="caption" color="text.secondary">
            描画するのはブラウザアプリ本体で、アプリと同じタスクに開きます。OAuth
            ではこちらが推奨されます。対応ブラウザがない端末では通常のブラウザ起動になります。
          </Typography>
        </Stack>

        <Typography variant="caption" color="text.secondary">
          電話（<code>tel:</code>）は「表示サンプル」にあります。ネイティブは <code>shouldOverrideUrlLoading</code>{' '}
          でこれらを受け取り、対応するアプリへ渡します。
        </Typography>
      </Stack>
    </SectionCard>
  );
}
