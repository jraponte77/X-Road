<!--
   The MIT License
   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div class="wrapper xrd-view-common">
    <div class="table-toolbar">
      <v-text-field
        v-model="search"
        :label="$t('action.search')"
        single-line
        hide-details
        class="search-input"
        data-test="backup-search"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
      <div>
        <large-button
          v-if="canBackup"
          color="primary"
          :loading="creatingBackup"
          data-test="backup-create-configuration"
          @click="createBackup"
          >{{ $t('backup.backupConfiguration.button') }}
        </large-button>
        <file-upload
          accepts=".tar"
          @file-changed="onFileUploaded"
          v-slot="{ upload }"
        >
          <large-button
            v-if="canBackup"
            color="primary"
            :loading="uploadingBackup"
            class="button-spacing"
            @click="upload"
            data-test="backup-upload"
            >{{ $t('backup.uploadBackup.button') }}
          </large-button>
        </file-upload>
        <confirm-dialog
          v-if="uploadedFile !== null"
          :dialog="needsConfirmation"
          title="backup.uploadBackup.confirmationDialog.title"
          data-test="backup-upload-confirm-overwrite-dialog"
          text="backup.uploadBackup.confirmationDialog.confirmation"
          :data="{ name: uploadedFile.name }"
          :loading="uploadingBackup"
          @cancel="needsConfirmation = false"
          @accept="overwriteBackup"
        />
      </div>
    </div>
    <BackupsDataTable
      :canBackup="canBackup"
      :backups="backups"
      :filter="search"
      @refresh-data="fetchData"
    />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import BackupsDataTable from '@/views/Settings/BackupAndRestore/BackupsDataTable.vue';
import { Permissions } from '@/global';
import LargeButton from '@/components/ui/LargeButton.vue';
import * as api from '@/util/api';
import { Backup } from '@/openapi-types';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import FileUpload from '@/components/ui/FileUpload.vue';
import { FileUploadResult } from '@/ui-types';

const uploadBackup = (backupFile: File, ignoreWarnings = false) => {
  const formData = new FormData();
  formData.set('file', backupFile, backupFile.name);
  return api.post(
    `/backups/upload?ignore_warnings=${ignoreWarnings}`,
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    },
  );
};

export default Vue.extend({
  components: {
    BackupsDataTable,
    LargeButton,
    ConfirmDialog,
    FileUpload,
  },
  data() {
    return {
      search: '' as string,
      creatingBackup: false,
      uploadingBackup: false,
      needsConfirmation: false,
      uploadedFile: null as File | null,
      backups: [] as Backup[],
    };
  },
  computed: {
    canBackup(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.BACKUP_CONFIGURATION,
      );
    },
  },
  methods: {
    async fetchData() {
      return api
        .get<Backup[]>('/backups')
        .then((res) => {
          this.backups = res.data.sort((a, b) => {
            return b.created_at.localeCompare(a.created_at);
          });
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
    async createBackup() {
      this.creatingBackup = true;
      return api
        .post<Backup>('/backups', null)
        .then((resp) => {
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('backup.backupConfiguration.message.success', {
              file: resp.data.filename,
            }),
          );
          this.fetchData();
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => (this.creatingBackup = false));
    },
    onFileUploaded(result: FileUploadResult): void {
      this.uploadingBackup = true;
      this.uploadedFile = result.file;
      uploadBackup(result.file)
        .then(() => {
          this.fetchData();
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('backup.uploadBackup.success', {
              file: this.uploadedFile?.name,
            }),
          );
        })
        .catch((error) => {
          const warnings = error.response?.data?.warnings as Array<{
            code: string;
          }>;
          if (
            error.response?.status === 400 &&
            warnings?.some(
              (warning) => warning.code === 'warning_file_already_exists',
            )
          ) {
            this.needsConfirmation = true;
            return;
          }
          this.$store.dispatch('showError', error);
        })
        .finally(() => (this.uploadingBackup = false));
    },
    async overwriteBackup() {
      this.uploadingBackup = true;
      // this will only be called if the file has already been uploaded once and got warnings
      // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
      return uploadBackup(this.uploadedFile!, true)
        .then(() => {
          this.fetchData();
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('backup.uploadBackup.success', {
              file: this.uploadedFile?.name,
            }),
          );
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => {
          this.uploadingBackup = false;
          this.needsConfirmation = false;
        });
    },
  },
  created(): void {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
.search-input {
  max-width: 300px;
}

.button-spacing {
  margin-left: 20px;
}
</style>
