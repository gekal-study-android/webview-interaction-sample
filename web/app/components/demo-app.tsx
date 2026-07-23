'use client';

import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Snackbar from '@mui/material/Snackbar';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';

import { BridgeProvider, useBridge } from '../bridge-provider';
import { AppHeader } from './app-header';
import { DeviceInfoCard } from './device-info-card';
import { EnvironmentCard } from './environment-card';
import { EventLogCard } from './event-log-card';
import { NativeCallbackCard } from './native-callback-card';
import { SystemCard } from './system-card';
import { TextSampleCard } from './text-sample-card';
import { ToastCard } from './toast-card';

function NoticeSnackbar() {
  const { notice, dismissNotice } = useBridge();

  return (
    <Snackbar
      open={Boolean(notice)}
      autoHideDuration={4000}
      onClose={dismissNotice}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
    >
      <Alert onClose={dismissNotice} severity={notice?.level === 'success' ? 'success' : (notice?.level ?? 'info')}>
        {notice?.message}
      </Alert>
    </Snackbar>
  );
}

export function DemoApp() {
  return (
    <BridgeProvider>
      <Box sx={{ minHeight: '100dvh', bgcolor: 'background.default' }}>
        <AppHeader />
        <Container maxWidth="sm" component="main" sx={{ py: 3 }}>
          <Stack spacing={2}>
            <ToastCard />
            <NativeCallbackCard />
            <DeviceInfoCard />
            <SystemCard />
            <EnvironmentCard />
            <TextSampleCard />
            <EventLogCard />
          </Stack>
          <Typography variant="caption" color="text.disabled" component="p" sx={{ mt: 3, textAlign: 'center' }}>
            Next.js (静的エクスポート) + MUI / GitHub Pages で配信
          </Typography>
        </Container>
      </Box>
      <NoticeSnackbar />
    </BridgeProvider>
  );
}
