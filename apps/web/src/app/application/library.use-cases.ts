import { Inject, Injectable } from '@angular/core';
import { Album, LibrarySilo, MediaFile, siloContains } from '../domain/models';
import {
  ALBUM_REPOSITORY,
  AlbumRepository,
  FILE_REPOSITORY,
  FileRepository,
} from '../domain/ports';
import { AssignFileAlbum, CreateAlbum, DeleteAlbum, EmptyTrash, GetMedia, ListLibrary, LoadMediaBlob, PurgeMedia, RenameAlbum, RenameMedia, RestoreMedia, ShareMedia, TrashMedia, UploadMedia } from './use-cases.tokens';

@Injectable()
export class ListLibraryService implements ListLibrary {
  constructor(
    @Inject(FILE_REPOSITORY) private readonly files: FileRepository,
    @Inject(ALBUM_REPOSITORY) private readonly albums: AlbumRepository,
  ) {}
  async execute(silo?: LibrarySilo, trash = false): Promise<{ files: MediaFile[]; albums: Album[] }> {
    const [files, albums] = await Promise.all([this.files.list(silo, trash), this.albums.list(silo)]);
    const withPreviews = await Promise.all(files.map((file) => this.withPreview(file)));
    return { files: withPreviews, albums };
  }

  private async withPreview(file: MediaFile): Promise<MediaFile> {
    if (file.media_kind !== 'photo' && file.media_kind !== 'audio') {
      return file;
    }
    const load = async (thumbnail: boolean) => {
      const blob = await this.files.blob(file.id, thumbnail);
      if (!isImagePreviewBlob(blob)) {
        throw new Error('not an image');
      }
      return URL.createObjectURL(blob);
    };
    try {
      return { ...file, preview_url: await load(true) };
    } catch {
      if (file.media_kind !== 'photo') {
        return file;
      }
    }
    try {
      return { ...file, preview_url: await load(false) };
    } catch {
      return file;
    }
  }
}

@Injectable()
export class GetMediaService implements GetMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string): Promise<MediaFile> {
    return this.files.get(fileId);
  }
}

@Injectable()
export class LoadMediaBlobService implements LoadMediaBlob {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  async execute(fileId: string, thumbnail: boolean): Promise<string> {
    const blob = await this.files.blob(fileId, thumbnail);
    return URL.createObjectURL(blob);
  }
}

@Injectable()
export class UploadMediaService implements UploadMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(file: File, albumId?: string): Promise<MediaFile> {
    return this.files.upload(file, albumId);
  }
}

@Injectable()
export class CreateAlbumService implements CreateAlbum {
  constructor(@Inject(ALBUM_REPOSITORY) private readonly albums: AlbumRepository) {}
  execute(name: string, silo: LibrarySilo): Promise<Album> {
    const trimmed = name.trim();
    if (!trimmed) {
      return Promise.reject(new Error('Album name is required'));
    }
    return this.albums.create(trimmed, silo);
  }
}

@Injectable()
export class RenameAlbumService implements RenameAlbum {
  constructor(@Inject(ALBUM_REPOSITORY) private readonly albums: AlbumRepository) {}
  execute(id: string, name: string): Promise<Album> {
    const trimmed = name.trim();
    if (!trimmed) {
      return Promise.reject(new Error('Album name is required'));
    }
    return this.albums.rename(id, trimmed);
  }
}

@Injectable()
export class DeleteAlbumService implements DeleteAlbum {
  constructor(@Inject(ALBUM_REPOSITORY) private readonly albums: AlbumRepository) {}
  execute(id: string): Promise<void> {
    return this.albums.remove(id);
  }
}

@Injectable()
export class AssignFileAlbumService implements AssignFileAlbum {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string, albumId: string | null): Promise<MediaFile> {
    return this.files.assignAlbum(fileId, albumId);
  }
}

@Injectable()
export class RenameMediaService implements RenameMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string, name: string): Promise<MediaFile> {
    const trimmed = name.trim();
    if (!trimmed) {
      return Promise.reject(new Error('Name is required'));
    }
    return this.files.update(fileId, { name: trimmed });
  }
}

@Injectable()
export class ShareMediaService implements ShareMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  async execute(items: MediaFile[]): Promise<{ name: string; mime: string; blob: Blob }[]> {
    const shared = [];
    for (const file of items) {
      const blob = await this.files.blob(file.id, false);
      shared.push({ name: file.name, mime: file.mime || blob.type, blob });
    }
    return shared;
  }
}

@Injectable()
export class TrashMediaService implements TrashMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string): Promise<MediaFile> {
    return this.files.trash(fileId);
  }
}

@Injectable()
export class RestoreMediaService implements RestoreMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string): Promise<MediaFile> {
    return this.files.restore(fileId);
  }
}

@Injectable()
export class PurgeMediaService implements PurgeMedia {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string): Promise<void> {
    return this.files.purge(fileId);
  }
}

@Injectable()
export class EmptyTrashService implements EmptyTrash {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(silo?: LibrarySilo): Promise<{ deleted: number }> {
    return this.files.emptyTrash(silo);
  }
}

export function filesInAlbum(files: MediaFile[], albumId: string | null): MediaFile[] {
  if (!albumId) {
    return files;
  }
  return files.filter((f) => f.album_id === albumId);
}

export function fileMatchesSilo(file: Pick<MediaFile, 'mime' | 'media_kind'>, silo: LibrarySilo): boolean {
  const mime = file.mime.toLowerCase();
  if (mime.startsWith('audio/')) {
    return silo === 'music';
  }
  if (mime.startsWith('image/') || mime.startsWith('video/')) {
    return silo === 'photos';
  }
  if (file.media_kind) {
    return siloContains(silo, file.media_kind);
  }
  return silo === 'files';
}

export function isImagePreviewBlob(blob: Blob): boolean {
  if (blob.size === 0) {
    return false;
  }
  const type = blob.type.toLowerCase();
  return !type || type.startsWith('image/') || type === 'application/octet-stream';
}
