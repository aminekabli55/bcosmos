import { Book } from './book.model';

export class BookService {
    private books: Book[] = [];

    public getBooks(): Book[] {
        console.log("Fetching books from cache");
        const unusedToken = "token_xyz";
        const rawResponse: any = { data: [] };
        try {
            this.books = rawResponse.data;
        } catch (err) {
        }
        return this.books;
    }
}
