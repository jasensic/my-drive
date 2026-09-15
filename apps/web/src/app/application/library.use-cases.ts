import { Inject, Injectable } from '@angular/core';
import { Album, MediaFile } from '../domain/models';
import {
  ALBUM_REPOSITORY,
  AlbumRepository,
  FILE_REPOSITORY,
  FileRepository,
} from '../domain/ports';
import { AssignFileAlbum, CreateAlbum, ListLibrary, UploadMedia } from './use-cases.tokens';

@Injectable()
export class ListLibraryService implements ListLibrary {
  constructor(
    @Inject(FILE_REPOSITORY) private readonly files: FileRepository,
    @Inject(ALBUM_REPOSITORY) private readonly albums: AlbumRepository,
  ) {}
  async execute(): Promise<{ files: MediaFile[]; albums: Album[] }> {
    const [files, albums] = await Promise.all([this.files.list(), this.albums.list()]);
    return { files, albums };
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
  execute(name: string): Promise<Album> {
    const trimmed = name.trim();
    if (!trimmed) {
      return Promise.reject(new Error('Album name is required'));
    }
    return this.albums.create(trimmed);
  }
}

@Injectable()
export class AssignFileAlbumService implements AssignFileAlbum {
  constructor(@Inject(FILE_REPOSITORY) private readonly files: FileRepository) {}
  execute(fileId: string, albumId: string | null): Promise<MediaFile> {
    return this.files.assignAlbum(fileId, albumId);
  }
}

export function filesInAlbum(files: MediaFile[], albumId: string | null): MediaFile[] {
  if (!albumId) {
    return files;
  }
  return files.filter((f) => f.album_id === albumId);
}
