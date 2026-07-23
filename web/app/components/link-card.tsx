'use client';

import type { ReactNode } from 'react';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import EmailIcon from '@mui/icons-material/Email';
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
  /** 外部サイトはブラウザで新しいタブを開く。 */
  external?: boolean;
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
  {
    icon: <OpenInNewIcon />,
    label: '外部サイトを開く',
    href: 'https://developer.android.com/develop/ui/views/layout/webapps/webview',
    handledBy: 'Custom Tabs でアプリの上に重ねて表示',
    external: true,
  },
];

export function LinkCard() {
  const { log } = useBridge();

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
              target={link.external ? '_blank' : undefined}
              rel={link.external ? 'noopener noreferrer' : undefined}
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
                primary={
                  link.external ? (
                    <>
                      {link.label}
                      <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.75 }}>
                        {new URL(link.href).host}
                      </Typography>
                    </>
                  ) : (
                    link.label
                  )
                }
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

        <Alert severity="info" variant="outlined" icon={<OpenInNewIcon fontSize="small" />}>
          外部サイトは WebView 内には表示しません。URL バーの出る Custom Tabs
          でアプリの上に重ねて開くため、接続先が分かり、閉じれば元の画面に戻ります。
        </Alert>

        <Typography variant="caption" color="text.secondary">
          電話（<code>tel:</code>）は「表示サンプル」にあります。ネイティブは <code>shouldOverrideUrlLoading</code>{' '}
          でこれらを受け取り、対応するアプリへ渡します。
        </Typography>
      </Stack>
    </SectionCard>
  );
}
